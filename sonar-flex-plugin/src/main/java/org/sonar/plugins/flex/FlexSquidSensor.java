/*
 * SonarQube Flex Plugin
 * Copyright (C) 2010-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.flex;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.RecognitionException;
import com.sonar.sslr.impl.Parser;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.Checks;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.ce.measure.RangeDistributionBuilder;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.measures.Metric;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.flex.FlexCheck;
import org.sonar.flex.FlexGrammar;
import org.sonar.flex.FlexVisitorContext;
import org.sonar.flex.Issue;
import org.sonar.flex.checks.CheckList;
import org.sonar.flex.lexer.FlexLexer;
import org.sonar.flex.metrics.ComplexityVisitor;
import org.sonar.flex.metrics.FileMetrics;
import org.sonar.flex.parser.FlexParser;
import org.sonar.plugins.flex.core.Flex;
import org.sonar.squidbridge.ProgressReport;
import org.sonar.sslr.parser.LexerlessGrammar;

public class FlexSquidSensor implements Sensor {

  private static final Logger LOG = Loggers.get(FlexSquidSensor.class);

  private static final Number[] FUNCTIONS_DISTRIB_BOTTOM_LIMITS = {1, 2, 4, 6, 8, 10, 12};
  private static final Number[] FILES_DISTRIB_BOTTOM_LIMITS = {0, 5, 10, 20, 30, 60, 90};

  private final Checks<FlexCheck> checks;
  private final FileLinesContextFactory fileLinesContextFactory;

  public FlexSquidSensor(CheckFactory checkFactory, FileLinesContextFactory fileLinesContextFactory) {
    this.checks = checkFactory
      .<FlexCheck>create(CheckList.REPOSITORY_KEY)
      .addAnnotatedChecks((Iterable) CheckList.getChecks());
    this.fileLinesContextFactory = fileLinesContextFactory;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .name("Flex")
      .onlyOnFileType(InputFile.Type.MAIN)
      .onlyOnLanguage(Flex.KEY);
  }

  @Override
  public void execute(SensorContext context) {
    FileSystem fileSystem = context.fileSystem();
    FilePredicates predicates = fileSystem.predicates();

    FilePredicate filePredicate = predicates.and(
      predicates.hasType(InputFile.Type.MAIN),
      predicates.hasLanguage(Flex.KEY),
      inputFile -> !inputFile.uri().getPath().endsWith("mxml"));

    List<InputFile> inputFiles = new ArrayList<>();
    fileSystem.inputFiles(filePredicate).forEach(inputFiles::add);

    ProgressReport progressReport = new ProgressReport("Report about progress of SonarFlex analyzer", TimeUnit.SECONDS.toMillis(10));
    List<String> filenames = inputFiles.stream().map(InputFile::toString).collect(Collectors.toList());
    progressReport.start(filenames);

    Charset charset = fileSystem.encoding();
    for (InputFile inputFile : inputFiles) {
      analyseFile(context, charset, inputFile);
      progressReport.nextFile();
    }

    progressReport.stop();
  }

  private void analyseFile(SensorContext context, Charset charset, InputFile inputFile) {
    String fileContent;
    try {
      fileContent = inputFile.contents();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + inputFile, e);
    }

    Parser<LexerlessGrammar> parser = FlexParser.create(charset);
    FlexVisitorContext visitorContext;
    try {
      visitorContext = new FlexVisitorContext(fileContent, parser.parse(fileContent));
      saveMeasures(context, inputFile, visitorContext);
    } catch (RecognitionException e) {
      visitorContext = new FlexVisitorContext(fileContent, e);
      LOG.error("Unable to parse file: {}", inputFile);
      LOG.error(e.getMessage());
    }

    for (FlexCheck check : checks.all()) {
      saveIssues(context, check, check.scanFileForIssues(visitorContext), inputFile);
    }

    new FlexTokensVisitor(context, FlexLexer.create(charset), inputFile).scanFile(visitorContext);
  }

  private void saveIssues(SensorContext context, FlexCheck check, List<Issue> issues, InputFile inputFile) {
    for (Issue flexIssue : issues) {
      RuleKey ruleKey = checks.ruleKey(check);
      NewIssue issue = context.newIssue();
      NewIssueLocation location = issue.newLocation()
        .on(inputFile)
        .message(flexIssue.message());
      Integer line = flexIssue.line();
      if (line != null) {
        location.at(inputFile.selectLine(line));
      }
      Double cost = flexIssue.cost();
      if (cost != null) {
        issue.gap(cost);
      }
      issue.at(location).forRule(ruleKey).save();
    }
  }

  private void saveMeasures(SensorContext context, InputFile inputFile, FlexVisitorContext visitorContext) {
    FileMetrics metrics = new FileMetrics(visitorContext);
    saveMeasure(context, inputFile, CoreMetrics.NCLOC, metrics.linesOfCode().size());
    saveMeasure(context, inputFile, CoreMetrics.COMMENT_LINES, metrics.commentLines().size());
    saveMeasure(context, inputFile, CoreMetrics.CLASSES, metrics.numberOfClasses());
    saveMeasure(context, inputFile, CoreMetrics.FUNCTIONS, metrics.numberOfFunctions());
    saveMeasure(context, inputFile, CoreMetrics.STATEMENTS, metrics.numberOfStatements());

    FileLinesContext fileLinesContext = fileLinesContextFactory.createFor(inputFile);
    metrics.linesOfCode().forEach(line -> fileLinesContext.setIntValue(CoreMetrics.NCLOC_DATA_KEY, line, 1));
    metrics.commentLines().forEach(line -> fileLinesContext.setIntValue(CoreMetrics.COMMENT_LINES_DATA_KEY, line, 1));
    fileLinesContext.save();

    AstNode root = visitorContext.rootTree();
    int fileComplexity = ComplexityVisitor.complexity(root);
    saveMeasure(context, inputFile, CoreMetrics.COMPLEXITY, fileComplexity);
    saveClassComplexity(context, inputFile, root);
    saveFunctionsComplexityDistribution(context, inputFile, root);
    saveFilesComplexityDistribution(context, inputFile, fileComplexity);
  }

  private static void saveClassComplexity(SensorContext context, InputFile inputFile, AstNode rootNode) {
    int complexityInClasses = 0;
    for (AstNode classDef : rootNode.getDescendants(FlexGrammar.CLASS_DEF, FlexGrammar.INTERFACE_DEF)) {
      int classComplexity = ComplexityVisitor.complexity(classDef);
      complexityInClasses += classComplexity;
    }
    saveMeasure(context, inputFile, CoreMetrics.COMPLEXITY_IN_CLASSES, complexityInClasses);
  }

  private static void saveFunctionsComplexityDistribution(SensorContext context, InputFile inputFile, AstNode rootNode) {
    RangeDistributionBuilder complexityDistribution = new RangeDistributionBuilder(FUNCTIONS_DISTRIB_BOTTOM_LIMITS);
    for (AstNode functionDef : rootNode.getDescendants(FlexGrammar.FUNCTION_DEF, FlexGrammar.FUNCTION_EXPR)) {
      complexityDistribution.add(ComplexityVisitor.complexity(functionDef));
    }
    context.<String>newMeasure()
      .on(inputFile)
      .forMetric(CoreMetrics.FUNCTION_COMPLEXITY_DISTRIBUTION)
      .withValue(complexityDistribution.build())
      .save();
  }

  private static void saveFilesComplexityDistribution(SensorContext context, InputFile inputFile, int fileComplexity) {
    String distribution = new RangeDistributionBuilder(FILES_DISTRIB_BOTTOM_LIMITS)
      .add(fileComplexity)
      .build();
    context.<String>newMeasure()
      .on(inputFile)
      .forMetric(CoreMetrics.FILE_COMPLEXITY_DISTRIBUTION)
      .withValue(distribution)
      .save();
  }

  private static void saveMeasure(SensorContext context, InputFile inputFile, Metric<Integer> metric, int value) {
    context.<Integer>newMeasure()
      .on(inputFile)
      .forMetric(metric)
      .withValue(value)
      .save();
  }

}

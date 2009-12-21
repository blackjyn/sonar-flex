/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2009 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.flex.flexmetrics;

import org.apache.commons.lang.StringUtils;
import org.sonar.plugins.flex.FlexPackage;
import org.sonar.plugins.flex.FlexFile;
import org.sonar.plugins.flex.Flex;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.maven.DependsUponMavenPlugin;
import org.sonar.api.batch.maven.MavenPluginHandler;
import org.sonar.api.utils.XpathParser;
import org.sonar.api.utils.XmlParserException;
import org.sonar.api.utils.ParsingUtils;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.RangeDistributionBuilder;
import org.sonar.api.measures.PersistenceMode;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

public class FlexMetricsSensor implements Sensor, DependsUponMavenPlugin {

  private FlexMetricsMavenPluginHandler pluginHandler;
  public static final Number[] FUNCTIONS_DISTRIB_BOTTOM_LIMITS = {1, 2, 4, 6, 8, 10, 12};

  public FlexMetricsSensor(FlexMetricsMavenPluginHandler pluginHandler) {
    this.pluginHandler = pluginHandler;
  }

  public boolean shouldExecuteOnProject(Project project) {
    return project.getLanguage().equals(Flex.INSTANCE);
  }

  public void analyse(Project project, SensorContext context) {
    File xmlFile = new File(project.getFileSystem().getBuildDir(), "javancss-raw-report.xml");
    XpathParser parser = new XpathParser();
    parser.parse(xmlFile);
    parseFlexMetricsReport(context, parser);
  }

  private void parseFlexMetricsReport(SensorContext context, XpathParser parser) {
    try {
      collectPackageMeasures(parser, context);
      collectFileMeasures(parser, context);
      createComplexityClassMeasures(parser, context);
    }
    catch (ParseException e) {
      throw new XmlParserException(e);
    }
  }

  public MavenPluginHandler getMavenPluginHandler(Project project) {
    return pluginHandler;
  }

  protected void collectPackageMeasures(XpathParser parser, SensorContext context) throws ParseException {
    NodeList packages = parser.executeXPathNodeList("/javancss/packages/package");
    if (packages != null) {
      for (int i = 0; i < packages.getLength(); i++) {
        Element element = (Element) packages.item(i);
        String packageName = parser.getChildElementValue(element, "name");
        if (".".equals(packageName)) {
          packageName = null;
        }

        Resource flexPackage = new FlexPackage(packageName);

        double lines = ParsingUtils.parseNumber(parser.getChildElementValue(element, "javadoc_lines")) +
                       ParsingUtils.parseNumber(parser.getChildElementValue(element, "single_comment_lines")) +
                       ParsingUtils.parseNumber(parser.getChildElementValue(element, "multi_comment_lines"));
        context.saveMeasure(flexPackage, CoreMetrics.COMMENT_LINES, lines);
        context.saveMeasure(flexPackage, CoreMetrics.PACKAGES, 1.0);
      }
    }
  }

  protected void collectFileMeasures(XpathParser parser, SensorContext context) throws ParseException {
    NodeList classes = parser.executeXPathNodeList("/javancss/objects/object");
    if (classes != null) {
      for (int i = 0; i < classes.getLength(); i++) {
        Element element = (Element) classes.item(i);
        Resource clazz = new FlexFile(parser.getChildElementValue(element, "name"));

        createClassMeasure(context, clazz, CoreMetrics.NCLOC, element, "ncss", parser);
        createClassMeasure(context, clazz, CoreMetrics.FUNCTIONS, element, "functions", parser);
        context.saveMeasure(clazz, CoreMetrics.CLASSES, 1.0);
      }
    }
  }

  private void createClassMeasure(SensorContext context, Resource clazz, Metric metric, Element classElement, String key, XpathParser parser) throws ParseException {
    Element element = parser.getChildElement(classElement, key);
    if (element != null && element.getFirstChild() != null && element.getFirstChild().getNodeValue() != null) {
      String value = element.getFirstChild().getNodeValue();
      context.saveMeasure(clazz, metric, ParsingUtils.parseNumber(value));
    }
  }

  protected void createComplexityClassMeasures(XpathParser parser, SensorContext context) throws ParseException {
    NodeList functions = parser.executeXPathNodeList("/javancss/functions/function");
    Map<String, Integer> ccnCountperClass = new HashMap<String, Integer>();
    Map<String, RangeDistributionBuilder> ccnDistributionPerClass = new HashMap<String, RangeDistributionBuilder>();
    if (functions != null) {
      for (int i = 0; i < functions.getLength(); i++) {
        Element element = (Element) functions.item(i);
        String fullFunctionName = parser.getChildElementValue(element, "name");
        Integer ccnForFunction = new Integer(parser.getChildElementValue(element, "ccn"));
        String packageAndClassName = getPackageAndClassFromFunction(fullFunctionName);
        addUpComplexityToClass(ccnCountperClass, ccnForFunction, packageAndClassName);
        addUpComplexityToClassDistribution(ccnDistributionPerClass, ccnForFunction, packageAndClassName);
      }
    }
    for (String fullname : ccnCountperClass.keySet()) {
      Integer ccnForClass = ccnCountperClass.get(fullname);
      context.saveMeasure(new FlexFile(fullname), CoreMetrics.COMPLEXITY, ccnForClass.doubleValue());
    }
    for (String fullname : ccnDistributionPerClass.keySet()) {
      RangeDistributionBuilder ccnDistributionForClass = ccnDistributionPerClass.get(fullname);
      context.saveMeasure(new FlexFile(fullname), ccnDistributionForClass.build().setPersistenceMode(PersistenceMode.MEMORY));
    }
  }

  private void addUpComplexityToClass(Map<String, Integer> ccnCountperClass, Integer ccnForFunction, String packageAndClassName) {
    Integer ccnSum = ccnCountperClass.get(packageAndClassName);
    if (ccnSum == null) {
      ccnSum = 0;
    }
    ccnSum += ccnForFunction;
    ccnCountperClass.put(packageAndClassName, ccnSum);
  }

  private void addUpComplexityToClassDistribution(Map<String, RangeDistributionBuilder> ccnDistributionperClass, Integer ccnForFunction, String packageAndClassName) {
    RangeDistributionBuilder ccnDistribution = ccnDistributionperClass.get(packageAndClassName);
    if (ccnDistribution == null) {
      ccnDistribution = new RangeDistributionBuilder(CoreMetrics.FUNCTION_COMPLEXITY_DISTRIBUTION, FUNCTIONS_DISTRIB_BOTTOM_LIMITS);
      ccnDistributionperClass.put(packageAndClassName, ccnDistribution);
    }
    ccnDistribution.add(ccnForFunction);
  }

  protected String getPackageAndClassFromFunction(String functionName) {
    return StringUtils.substringBefore(functionName, "::");
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
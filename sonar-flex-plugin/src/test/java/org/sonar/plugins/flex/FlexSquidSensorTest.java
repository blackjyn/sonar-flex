/*
 * SonarQube Flex Plugin
 * Copyright (C) 2010-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import org.fest.assertions.Assertions;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.resources.Project;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.plugins.flex.core.Flex;
import org.sonar.test.TestUtils;

import java.io.File;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FlexSquidSensorTest {

  private static final String TEST_DIR = "org/sonar/plugins/flex/squid/";

  @Test
  public void test_should_execute_on_project() {
    DefaultFileSystem fs = new DefaultFileSystem(new File("."));
    FlexSquidSensor sensor = newSquidSensor(fs);

    // No Flex file in file system
    Assertions.assertThat(sensor.shouldExecuteOnProject(new Project("key"))).isFalse();

    // With Flex source file
    fs.add(new DefaultInputFile("key", "Dummy.as").setLanguage(Flex.KEY).setType(InputFile.Type.MAIN));
    Assertions.assertThat(sensor.shouldExecuteOnProject(new Project("key"))).isTrue();
  }

  @Test
  public void should_analyse() {
    DefaultFileSystem fs = new DefaultFileSystem(TestUtils.getResource("org/sonar/plugins/flex/duplications/"));
    fs.add(newInputFile("SmallFile.as"));

    SensorContext context = mock(SensorContext.class);

    newSquidSensor(fs).analyse(new Project("key"), context);

    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.COMPLEXITY_IN_CLASSES), Mockito.eq(1.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.NCLOC), Mockito.eq(9.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.COMMENT_LINES), Mockito.eq(1.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.STATEMENTS), Mockito.eq(2.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.FUNCTIONS), Mockito.eq(2.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.CLASSES), Mockito.eq(1.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.COMPLEXITY), Mockito.eq(2.0));
  }

  @Test
  public void should_analyse2() {
    DefaultFileSystem fs = new DefaultFileSystem(TestUtils.getResource(TEST_DIR));
    fs.add(newInputFile("TimeFormatter.as"));

    SensorContext context = mock(SensorContext.class);

    newSquidSensor(fs).analyse(new Project("key"), context);

    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.COMPLEXITY_IN_CLASSES), Mockito.eq(0.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.NCLOC), Mockito.eq(0.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.COMMENT_LINES), Mockito.eq(59.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.FUNCTIONS), Mockito.eq(0.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.CLASSES), Mockito.eq(0.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.STATEMENTS), Mockito.eq(0.0));
    verify(context).saveMeasure(Mockito.any(InputFile.class), Mockito.eq(CoreMetrics.COMPLEXITY), Mockito.eq(0.0));
  }

  private FlexSquidSensor newSquidSensor(DefaultFileSystem fs) {
    FileLinesContextFactory fileLinesContextFactory = mock(FileLinesContextFactory.class);
    when(fileLinesContextFactory.createFor(Mockito.any(InputFile.class))).thenReturn(mock(FileLinesContext.class));

    CheckFactory checkFactory = new CheckFactory(mock(ActiveRules.class));
    return new FlexSquidSensor(checkFactory, fileLinesContextFactory, fs, mock(ResourcePerspectives.class), new PathResolver());
  }

  private DefaultInputFile newInputFile(String path) {
    return new DefaultInputFile("key", path)
      .setLanguage(Flex.KEY)
      .setType(InputFile.Type.MAIN);
  }
}

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

import org.sonar.api.Plugin;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;
import org.sonar.plugins.flex.cobertura.CoberturaSensor;
import org.sonar.plugins.flex.core.Flex;

public class FlexPlugin implements Plugin {

  public static final String FILE_SUFFIXES_KEY = "sonar.flex.file.suffixes";
  public static final String COBERTURA_REPORT_PATH = "sonar.flex.cobertura.reportPath";

  @Override
  public void define(Context context) {
    context.addExtensions(
      Flex.class,

      FlexSquidSensor.class,
      CoberturaSensor.class,

      FlexRulesDefinition.class,
      FlexProfile.class,

      PropertyDefinition.builder(FILE_SUFFIXES_KEY)
        .defaultValue(Flex.DEFAULT_FILE_SUFFIXES)
        .name("File suffixes")
        .description("Comma-separated list of suffixes for files to analyze. To not filter, leave the list empty.")
        .onQualifiers(Qualifiers.MODULE, Qualifiers.PROJECT)
        .category(Flex.NAME)
        .multiValues(true)
        .build(),

      PropertyDefinition.builder(COBERTURA_REPORT_PATH)
        .name("Cobertura xml report path")
        .description("Path to the Cobertura coverage report file. The path may be either absolute or relative to the project base directory.")
        .onQualifiers(Qualifiers.MODULE, Qualifiers.PROJECT)
        .category(Flex.NAME)
        .build());
  }
}

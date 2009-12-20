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

package org.sonar.plugins.flex.colorizer;

import org.sonar.api.web.CodeColorizerFormat;
import org.sonar.colorizer.*;
import org.sonar.plugins.flex.Flex;

import java.util.Arrays;
import java.util.List;
import java.util.Collections;

public class FlexColorizerFormat extends CodeColorizerFormat {

  public FlexColorizerFormat() {
    super(Flex.KEY);
  }

  @Override
  public List<Tokenizer> getTokenizers() {
    return Collections.unmodifiableList(Arrays.asList(
      new StringTokenizer("<span class=\"s\">", "</span>"),
      new CDocTokenizer("<span class=\"cd\">", "</span>"),
      new JavadocTokenizer("<span class=\"cppd\">", "</span>"),
      new CppDocTokenizer("<span class=\"cppd\">", "</span>"),
      new KeywordsTokenizer("<span class=\"k\">", "</span>", FlexKeywords.get())
    ));
  }
}

/*
 * Sonar Flex Plugin
 * Copyright (C) 2010 SonarSource
 * dev@sonar.codehaus.org
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

package org.sonar.plugins.flex.core;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration.Configuration;
import org.junit.Test;
import org.sonar.api.resources.Directory;
import org.sonar.api.resources.File;
import org.sonar.plugins.flex.FlexPlugin;

public class FlexTest {

  @Test
  public void testGetFileSuffixes() {
    Configuration configuration = mock(Configuration.class);
    Flex flex = new Flex(configuration);

    when(configuration.getStringArray(FlexPlugin.FILE_SUFFIXES_KEY)).thenReturn(null);
    assertThat(flex.getFileSuffixes(), is(new String[] {"as"}));
  }

}
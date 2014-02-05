/*
 * SonarQube Flex Plugin
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
package org.sonar.flex.checks;

import com.google.common.collect.Maps;
import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.squid.checks.SquidCheck;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.flex.FlexGrammar;
import org.sonar.flex.FlexKeyword;
import org.sonar.flex.checks.utils.Clazz;
import org.sonar.flex.checks.utils.Function;
import org.sonar.flex.checks.utils.Modifiers;
import org.sonar.sslr.parser.LexerlessGrammar;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

@Rule(
  key = "S1144",
  priority = Priority.MAJOR)
@BelongsToProfile(title = CheckList.SONAR_WAY_PROFILE, priority = Priority.MAJOR)
public class UnusedPrivateFunctionCheck extends SquidCheck<LexerlessGrammar> {

  static class PrivateFunction {
    final AstNode declaration;
    int usages;

    public PrivateFunction(AstNode declaration, int usages) {
      this.declaration = declaration;
      this.usages = usages;
    }
  }

  private static class ClassState {
    private final Map<String, PrivateFunction> functions;

    public ClassState(AstNode classDef) {
      this.functions = Maps.newHashMap();
      retrieveAllPrivateFunctions(classDef);
    }

    private void retrieveAllPrivateFunctions(AstNode classDef) {
      for (AstNode functionDef : Clazz.getFunctions(classDef)) {

        if (!Function.isAccessor(functionDef)
          && Modifiers.getModifiers(functionDef.getPreviousAstNode()).contains(FlexKeyword.PRIVATE)
          && !Function.isEmptyConstructor(functionDef, Clazz.getName(classDef))) {
          AstNode identifierNode = functionDef
            .getFirstChild(FlexGrammar.FUNCTION_NAME)
            .getFirstChild(FlexGrammar.IDENTIFIER);

          functions.put(identifierNode.getTokenValue(), new PrivateFunction(identifierNode, 0));
        }
      }

    }

    private void use(AstNode astNode) {
      PrivateFunction function = functions.get(astNode.getTokenValue());

      if (function != null) {
        function.usages++;
        return;
      }
    }
  }

  private Deque<ClassState> classStack = new ArrayDeque<ClassState>();
  private boolean inClass;

  @Override
  public void init() {
    subscribeTo(
      FlexGrammar.CLASS_DEF,
      FlexGrammar.FUNCTION_DEF,
      FlexGrammar.QUALIFIED_IDENTIFIER);
  }

  @Override
  public void visitFile(@Nullable AstNode astNode) {
    classStack.clear();
  }

  @Override
  public void visitNode(AstNode astNode) {
    if (astNode.is(FlexGrammar.CLASS_DEF)) {
      inClass = true;
      classStack.push(new ClassState(astNode));
    } else if (inClass && astNode.is(FlexGrammar.QUALIFIED_IDENTIFIER)) {
      classStack.peek().use(astNode);
    }
  }


  @Override
  public void leaveNode(AstNode astNode) {
    if (astNode.is(FlexGrammar.CLASS_DEF)) {
      reportUnusedPrivateFunction();
      classStack.pop();
      inClass = classStack.isEmpty() ? false : true;
    }
  }

  private void reportUnusedPrivateFunction() {
    for (Map.Entry<String, PrivateFunction> entry : classStack.peek().functions.entrySet()) {
      if (entry.getValue().usages == 0) {
        getContext().createLineViolation(this, "Remove the declaration of the unused '" + entry.getKey() + "' function.", entry.getValue().declaration);
      }
    }
  }
}

// Copyright 2012 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.collide.client.code.lang;

import org.waveprotocol.wave.client.common.util.SignalEvent;

import com.google.collide.client.editor.Editor;
import com.google.collide.client.editor.input.ActionExecutor;
import com.google.collide.client.editor.input.CommonActions;
import com.google.collide.client.editor.input.DefaultActionExecutor;
import com.google.collide.client.editor.input.InputScheme;
import com.google.collide.client.editor.input.Shortcut;
import com.google.gwt.regexp.shared.RegExp;

/**
 * JS-specific implementation {@link LanguageHelper}.
 */
class JsLanguageHelper extends DefaultActionExecutor implements LanguageHelper {

  private static final RegExp COMMENT_CHECKER = RegExp.compile("^\\s*//");

  JsLanguageHelper() {
    addAction(CommonActions.TOGGLE_COMMENT, new Shortcut(){
      @Override
      public boolean event(InputScheme scheme, SignalEvent event) {
        return toggleComments(scheme.getInputController().getEditor());
      }
    });
  }

  public boolean toggleComments(Editor editor) {
    editor.getSelection().toggleComments(editor.getEditorDocumentMutator(), COMMENT_CHECKER, "//");
    return true;
  }

  @Override
  public ActionExecutor getActionExecutor() {
    return this;
  }
}

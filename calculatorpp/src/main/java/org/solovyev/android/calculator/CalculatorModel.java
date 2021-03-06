/*
 * Copyright (c) 2009-2011. Created by serso aka se.solovyev.
 * For more information, please, contact se.solovyev@gmail.com
 */

package org.solovyev.android.calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.solovyev.android.CursorControl;
import org.solovyev.android.calculator.history.CalculatorHistory;
import org.solovyev.android.calculator.history.CalculatorHistoryState;
import org.solovyev.android.calculator.history.TextViewEditorAdapter;
import org.solovyev.android.calculator.jscl.JsclOperation;
import org.solovyev.android.calculator.math.MathType;
import org.solovyev.android.calculator.model.CalculatorEngine;
import org.solovyev.android.calculator.model.CalculatorEvalException;
import org.solovyev.android.calculator.model.CalculatorParseException;
import org.solovyev.android.history.HistoryControl;
import org.solovyev.android.menu.AMenuBuilder;
import org.solovyev.android.menu.MenuImpl;
import org.solovyev.common.msg.Message;
import org.solovyev.common.utils.MutableObject;
import org.solovyev.common.utils.StringUtils;
import org.solovyev.common.utils.history.HistoryAction;

import java.util.ArrayList;
import java.util.List;

/**
 * User: serso
 * Date: 9/12/11
 * Time: 11:15 PM
 */
public enum CalculatorModel implements CursorControl, HistoryControl<CalculatorHistoryState>, CalculatorEngineControl {

	instance;

	// millis to wait before evaluation after user edit action
	public static final int EVAL_DELAY_MILLIS = 0;

	@NotNull
	private CalculatorEditor editor;

	@NotNull
	private CalculatorDisplay display;

	@NotNull
	private CalculatorEngine calculatorEngine;

	public CalculatorModel init(@NotNull final Activity activity, @NotNull SharedPreferences preferences, @NotNull CalculatorEngine calculator) {
		Log.d(this.getClass().getName(), "CalculatorModel initialization with activity: " + activity);
		this.calculatorEngine = calculator;

		this.editor = (CalculatorEditor) activity.findViewById(R.id.calculatorEditor);
		this.editor.init(preferences);
		preferences.registerOnSharedPreferenceChangeListener(editor);

		this.display = (CalculatorDisplay) activity.findViewById(R.id.calculatorDisplay);
		this.display.setOnClickListener(new CalculatorDisplayOnClickListener(activity));

		final CalculatorHistoryState lastState = CalculatorHistory.instance.getLastHistoryState();
		if (lastState == null) {
			saveHistoryState();
		} else {
			setCurrentHistoryState(lastState);
		}


		return this;
	}

	private static void showEvaluationError(@NotNull Activity activity, @NotNull final String errorMessage) {
		final LayoutInflater layoutInflater = (LayoutInflater) activity.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

		final View errorMessageView = layoutInflater.inflate(R.layout.display_error_message, null);
		((TextView) errorMessageView.findViewById(R.id.error_message_text_view)).setText(errorMessage);

		final AlertDialog.Builder builder = new AlertDialog.Builder(activity)
				.setPositiveButton(R.string.c_cancel, null)
				.setView(errorMessageView);

		builder.create().show();
	}

	public void copyResult(@NotNull Context context) {
		copyResult(context, display);
	}

	public static void copyResult(@NotNull Context context, @NotNull final CalculatorDisplay display) {
		if (display.isValid()) {
			final CharSequence text = display.getText();
			if (!StringUtils.isEmpty(text)) {
				final ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Activity.CLIPBOARD_SERVICE);
				clipboard.setText(text.toString());
				Toast.makeText(context, context.getText(R.string.c_result_copied), Toast.LENGTH_SHORT).show();
			}
		}
	}

	private void saveHistoryState() {
		CalculatorHistory.instance.addState(getCurrentHistoryState());
	}

	public void setCursorOnStart() {
		editor.setSelection(0);
	}

	public void setCursorOnEnd() {
		editor.setSelection(editor.getText().length());
	}

	public void moveCursorLeft() {
		if (editor.getSelectionStart() > 0) {
			editor.setSelection(editor.getSelectionStart() - 1);
		}
	}

	public void moveCursorRight() {
		if (editor.getSelectionStart() < editor.getText().length()) {
			editor.setSelection(editor.getSelectionStart() + 1);
		}
	}

	public void doTextOperation(@NotNull TextOperation operation) {
		doTextOperation(operation, true);
	}

	public void doTextOperation(@NotNull TextOperation operation, boolean delayEvaluate) {
		doTextOperation(operation, delayEvaluate, JsclOperation.numeric, false);
	}

	public void doTextOperation(@NotNull TextOperation operation, boolean delayEvaluate, @NotNull JsclOperation jsclOperation, boolean forceEval) {
		final String editorStateBefore = this.editor.getText().toString();

		Log.d(CalculatorModel.class.getName(), "Editor state changed before '" + editorStateBefore + "'");
		operation.doOperation(this.editor);
		//Log.d(CalculatorModel.class.getName(), "Doing text operation" + StringUtils.fromStackTrace(Thread.currentThread().getStackTrace()));

		final String editorStateAfter = this.editor.getText().toString();
		if (forceEval ||!editorStateBefore.equals(editorStateAfter)) {

			editor.redraw();

			evaluate(delayEvaluate, editorStateAfter, jsclOperation, null);
		}
	}

	@NotNull
	private final static MutableObject<Runnable> pendingOperation = new MutableObject<Runnable>();

	private void evaluate(boolean delayEvaluate,
						  @NotNull final String expression,
						  @NotNull final JsclOperation operation,
						  @Nullable CalculatorHistoryState historyState) {

		final CalculatorHistoryState localHistoryState;
		if (historyState == null) {
			//this.display.setText("");
			localHistoryState = getCurrentHistoryState();
		} else {
			this.display.setText(historyState.getDisplayState().getEditorState().getText());
			localHistoryState = historyState;
		}

		pendingOperation.setObject(new Runnable() {
			@Override
			public void run() {
				// allow only one runner at one time
				synchronized (pendingOperation) {
					//lock all operations with history
					if (pendingOperation.getObject() == this) {
						// actually nothing shall be logged while text operations are done
						evaluate(expression, operation, this);

						if (pendingOperation.getObject() == this) {
							// todo serso: of course there is small probability that someone will set pendingOperation after if statement but before .setObject(null)
							pendingOperation.setObject(null);
							localHistoryState.setDisplayState(getCurrentHistoryState().getDisplayState());
						}
					}
				}
			}
		});

		if (delayEvaluate) {
			if (historyState == null) {
				CalculatorHistory.instance.addState(localHistoryState);
			}
            // todo serso: this is not correct - operation is processing still in the same thread
			new Handler().postDelayed(pendingOperation.getObject(), EVAL_DELAY_MILLIS);
		} else {
			pendingOperation.getObject().run();
			if (historyState == null) {
				CalculatorHistory.instance.addState(localHistoryState);
			}
		}
	}

	@Override
	public void evaluate() {
   		evaluate(false, this.editor.getText().toString(), JsclOperation.numeric, null);
	}

	public void evaluate(@NotNull JsclOperation operation) {
   		evaluate(false, this.editor.getText().toString(), operation, null);
	}

	@Override
	public void simplify() {
   		evaluate(false, this.editor.getText().toString(), JsclOperation.simplify, null);
	}

	private void evaluate(@Nullable final String expression,
						  @NotNull JsclOperation operation,
						  @NotNull Runnable currentRunner) {

		if (!StringUtils.isEmpty(expression)) {
			try {
				Log.d(CalculatorModel.class.getName(), "Trying to evaluate '" + operation + "': " + expression /*+ StringUtils.fromStackTrace(Thread.currentThread().getStackTrace())*/);
				final CalculatorEngine.Result result = calculatorEngine.evaluate(operation, expression);

				// todo serso: second condition might replaced with expression.equals(this.editor.getText().toString()) ONLY if expression will be formatted with text highlighter
				if (currentRunner == pendingOperation.getObject() && this.editor.getText().length() > 0) {
					display.setText(result.getResult());
				} else {
					display.setText("");
				}
				display.setJsclOperation(result.getUserOperation());
				display.setGenericResult(result.getGenericResult());
			} catch (CalculatorParseException e) {
				handleEvaluationException(expression, display, operation, e);
			} catch (CalculatorEvalException e) {
				handleEvaluationException(expression, display, operation, e);
			}
		} else {
			this.display.setText("");
			this.display.setJsclOperation(operation);
			this.display.setGenericResult(null);
		}



		this.display.redraw();
	}

	private void handleEvaluationException(@NotNull String expression,
										   @NotNull CalculatorDisplay localDisplay,
										   @NotNull JsclOperation operation,
										   @NotNull Message e) {
		Log.d(CalculatorModel.class.getName(), "Evaluation failed for : " + expression + ". Error message: " + e);
		if ( StringUtils.isEmpty(localDisplay.getText()) ) {
			// if previous display state was empty -> show error
			localDisplay.setText(R.string.c_syntax_error);
		} else {
			// show previous result instead of error caption (actually previous result will be greyed)
		}
		localDisplay.setJsclOperation(operation);
		localDisplay.setGenericResult(null);
		localDisplay.setValid(false);
		localDisplay.setErrorMessage(e.getLocalizedMessage());
	}

	public void clear() {
		if (!StringUtils.isEmpty(editor.getText()) || !StringUtils.isEmpty(display.getText())) {
			editor.getText().clear();
			display.setText("");
			saveHistoryState();
		}
	}

	public void processDigitButtonAction(@Nullable final String text) {
		processDigitButtonAction(text, true);
	}

	public void processDigitButtonAction(@Nullable final String text, boolean delayEvaluate) {

		if (!StringUtils.isEmpty(text)) {
			doTextOperation(new CalculatorModel.TextOperation() {

				@Override
				public void doOperation(@NotNull EditText editor) {
					int cursorPositionOffset = 0;
					final StringBuilder textToBeInserted = new StringBuilder(text);

					final MathType.Result mathType = MathType.getType(text, 0, false);
					switch (mathType.getMathType()) {
						case function:
							textToBeInserted.append("()");
							cursorPositionOffset = -1;
							break;
						case operator:
							textToBeInserted.append("()");
							cursorPositionOffset = -1;
							break;
						case comma:
							textToBeInserted.append(" ");
							break;
					}

					if (cursorPositionOffset == 0) {
						if (MathType.openGroupSymbols.contains(text)) {
							cursorPositionOffset = -1;
						}
					}

					editor.getText().insert(editor.getSelectionStart(), textToBeInserted.toString());
					editor.setSelection(editor.getSelectionStart() + cursorPositionOffset, editor.getSelectionEnd() + cursorPositionOffset);
				}
			}, delayEvaluate);
		}
	}

	public static interface TextOperation {

		void doOperation(@NotNull EditText editor);

	}

	@Override
	public void doHistoryAction(@NotNull HistoryAction historyAction) {
		synchronized (CalculatorHistory.instance) {
			if (CalculatorHistory.instance.isActionAvailable(historyAction)) {
				final CalculatorHistoryState newState = CalculatorHistory.instance.doAction(historyAction, getCurrentHistoryState());
				if (newState != null) {
					setCurrentHistoryState(newState);
				}
			}
		}
	}

	@Override
	public void setCurrentHistoryState(@NotNull CalculatorHistoryState editorHistoryState) {
		synchronized (CalculatorHistory.instance) {
			Log.d(this.getClass().getName(), "Saved history found: " + editorHistoryState);

			editorHistoryState.setValuesFromHistory(new TextViewEditorAdapter(this.editor), this.display);

			final String expression = this.editor.getText().toString();
			if ( !StringUtils.isEmpty(expression) ) {
				if ( StringUtils.isEmpty(this.display.getText().toString()) ) {
					evaluate(false, expression, this.display.getJsclOperation(), editorHistoryState);
				}
			}

			editor.redraw();
			display.redraw();
		}
	}

	@Override
	@NotNull
	public CalculatorHistoryState getCurrentHistoryState() {
		synchronized (CalculatorHistory.instance) {
			return CalculatorHistoryState.newInstance(new TextViewEditorAdapter(this.editor), this.display);
		}
	}

	@NotNull
	public CalculatorDisplay getDisplay() {
		return display;
	}

	private static class CalculatorDisplayOnClickListener implements View.OnClickListener {

		@NotNull
		private final Activity activity;

		public CalculatorDisplayOnClickListener(@NotNull Activity activity) {
			this.activity = activity;
		}

		@Override
		public void onClick(View v) {
            if (v instanceof CalculatorDisplay) {
                final CalculatorDisplay cd = (CalculatorDisplay) v;

                if (cd.isValid()) {
                    final List<CalculatorDisplay.MenuItem> filteredMenuItems = new ArrayList<CalculatorDisplay.MenuItem>(CalculatorDisplay.MenuItem.values().length);
                    for (CalculatorDisplay.MenuItem menuItem : CalculatorDisplay.MenuItem.values()) {
                        if (menuItem.isItemVisible(cd)) {
                            filteredMenuItems.add(menuItem);
                        }
                    }

                    if (!filteredMenuItems.isEmpty()) {
                        AMenuBuilder.newInstance(activity, MenuImpl.newInstance(filteredMenuItems)).create(cd).show();
                    }

                } else {
                    final String errorMessage = cd.getErrorMessage();
                    if (errorMessage != null) {
                        showEvaluationError(activity, errorMessage);
                    }
                }
            }
        }
    }
}

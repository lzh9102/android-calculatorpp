package org.solovyev.android.calculator;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import jscl.math.Generic;
import jscl.math.function.Constant;
import org.achartengine.ChartFactory;
import org.jetbrains.annotations.NotNull;
import org.solovyev.android.calculator.about.CalculatorAboutTabActivity;
import org.solovyev.android.calculator.help.CalculatorHelpTabActivity;
import org.solovyev.android.calculator.history.CalculatorHistoryTabActivity;
import org.solovyev.android.calculator.math.edit.CalculatorFunctionsTabActivity;
import org.solovyev.android.calculator.math.edit.CalculatorOperatorsActivity;
import org.solovyev.android.calculator.math.edit.CalculatorVarsTabActivity;
import org.solovyev.android.calculator.math.edit.CalculatorVarsActivity;
import org.solovyev.android.calculator.plot.CalculatorPlotActivity;
import org.solovyev.common.utils.StringUtils;

/**
 * User: serso
 * Date: 11/2/11
 * Time: 2:18 PM
 */
public class CalculatorActivityLauncher {

	public static void showHistory(@NotNull final Context context) {
		context.startActivity(new Intent(context, CalculatorHistoryTabActivity.class));
	}

	public static void showHelp(@NotNull final Context context) {
		context.startActivity(new Intent(context, CalculatorHelpTabActivity.class));
	}

	public static void showSettings(@NotNull final Context context) {
		context.startActivity(new Intent(context, CalculatorPreferencesActivity.class));
	}

	public static void showAbout(@NotNull final Context context) {
		context.startActivity(new Intent(context, CalculatorAboutTabActivity.class));
	}

	public static void showFunctions(@NotNull final Context context) {
		context.startActivity(new Intent(context, CalculatorFunctionsTabActivity.class));
	}

	public static void showOperators(@NotNull final Context context) {
		context.startActivity(new Intent(context, CalculatorOperatorsActivity.class));
	}

	public static void showVars(@NotNull final Context context) {
		context.startActivity(new Intent(context, CalculatorVarsTabActivity.class));
	}

	public static void plotGraph(@NotNull final Context context, @NotNull Generic generic, @NotNull Constant constant){
		final Intent intent = new Intent();
		intent.putExtra(ChartFactory.TITLE, context.getString(R.string.c_graph));
		intent.putExtra(CalculatorPlotActivity.INPUT, new CalculatorPlotActivity.Input(generic.toString(), constant.getName()));
		intent.setClass(context, CalculatorPlotActivity.class);
		context.startActivity(intent);
	}

	public static void createVar(@NotNull final Context context, @NotNull CalculatorModel calculatorModel) {
		if (calculatorModel.getDisplay().isValid() ) {
			final String varValue = calculatorModel.getDisplay().getText().toString();
			if (!StringUtils.isEmpty(varValue)) {
				if (CalculatorVarsActivity.isValidValue(varValue)) {
					final Intent intent = new Intent(context, CalculatorVarsTabActivity.class);
					intent.putExtra(CalculatorVarsActivity.CREATE_VAR_EXTRA_STRING, varValue);
					context.startActivity(intent);
				} else {
					Toast.makeText(context, R.string.c_not_valid_result, Toast.LENGTH_SHORT).show();
				}
			} else {
				Toast.makeText(context, R.string.c_empty_var_error, Toast.LENGTH_SHORT).show();
			}
		} else {
			Toast.makeText(context, R.string.c_not_valid_result, Toast.LENGTH_SHORT).show();
		}
	}
}

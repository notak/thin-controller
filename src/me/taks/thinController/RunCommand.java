package me.taks.thinController;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import me.taks.BaseActivity;

public class RunCommand extends BaseActivity {
	String command;
	SessionManager sessionManager;
	TextView outputPane;
	String[] paramHints;
	String[] params;
	String output;
	
    public void onCreate(Bundle savedInstanceState) {
        try {
	        super.onCreate(savedInstanceState);
	        setContentView(R.layout.run_command);
	        outputPane = (TextView)findViewById(R.id.outputPane);
	        outputPane.setVerticalScrollBarEnabled(true);
	        Bundle extras = getIntent().getExtras();
	        command = extras.getString("command");
	        paramHints = extras.getStringArray("paramHints");
			params = new String[paramHints.length];
        	//dumpError(new Exception(command));
        	sessionManager = SessionManager.get(extras.getString("host"), extras.getString("password"));
        	if (paramHints.length>0) showParamsDialog();
        	else launchRunThread();
        } catch (Exception e) { dumpError(e); }
  	
    }
    
    public void launchRunThread() {
		showDialog(DIALOG_RUNNING_COMMAND);
		// Fire off a thread to do some work that we shouldn't do directly in the UI thread
		Thread t = new Thread() { public void run() { 
        	runCommand();
        	mHandler.post(mUpdateResults); } };
        t.start();
    }

    // Need handler for callbacks to the UI thread
    final Handler mHandler = new Handler();

    // Create runnable for posting
    final Runnable mUpdateResults = new Runnable() { public void run() { updateDisplay(); } };    
    
    private static final int DIALOG_RUNNING_COMMAND = 100;
    public Dialog onCreateDialog(int id) {
    	switch (id) {
    	case DIALOG_RUNNING_COMMAND: return ProgressDialog.show(this, "", 
                "Running Command. Please wait...", true);
    	default: 
    		return super.onCreateDialog(id);
    	}
    }


    public void runCommand() {
		dismissDialog(DIALOG_RUNNING_COMMAND);
    	try { output=sessionManager.runSimpleCommand(command, params);
	    } catch (Exception e) { dumpError(e); }
    }

    public void updateDisplay() {
    	try { outputPane.setText(output);
	    } catch (Exception e) { dumpError(e); }
    }
    
    public void showParamsDialog() {
		AlertDialog.Builder builder;
		LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.params,
		                               (ViewGroup) findViewById(R.id.root));
		TableLayout table = ((TableLayout)layout.findViewById(R.id.paramsTable));
		for (String paramHint : paramHints) {
			TableRow tr = new TableRow(this);
			EditText label = new EditText(this);
			label.setHint(paramHint);
			label.setWidth(LayoutParams.FILL_PARENT);
			tr.addView(label);
			tr.addView(new CheckBox(this));
			table.addView(tr);
		}
		builder = new AlertDialog.Builder(this);
		builder.setView(layout)
			.setTitle("Enter Params")
			.setPositiveButton("Run", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					TableLayout paramsTable = ((TableLayout)((Dialog)dialog).findViewById(R.id.paramsTable));
					for (int i=0;i<paramsTable.getChildCount();i++) {
						TableRow tr = ((TableRow)paramsTable.getChildAt(i));
						String param = ((EditText)(tr.getChildAt(0))).getText().toString();
						CheckBox cb = ((CheckBox)tr.getChildAt(1));
						params[i]=cb.isChecked() ? quoted(param) : param;
					}
					launchRunThread();
				}
			});
		builder.show();
    }

}
package me.taks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.ClipboardManager;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import me.taks.thinController.R;

public class BaseActivity extends Activity {
    protected OnClickListener dialogOnClickListener;
    protected OnClickListener cancelOnClick = new OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {dialog.cancel(); }
	};
    private OnClickListener terminateOnClick = new OnClickListener() {
		public void onClick(DialogInterface dialog, int id) { BaseActivity.this.finish(); }
    };

	private static String getSimpleClassname(String classname) {
		return classname.substring(classname.lastIndexOf('.')+1);
	}
    public void dumpError(Exception e){
		StringBuffer sb = new StringBuffer();
		for(StackTraceElement ste : e.getStackTrace())
			sb.append(getSimpleClassname(ste.getClassName())+"."+ste.getMethodName()+
						" ("+ste.getFileName()+":"+ste.getLineNumber()+")<br>");
		String message = "<b>"+e.getMessage()+(e.getMessage().length()>0?"<br>":"")+
						e.getClass().getSimpleName()+"</b><br><small>"+sb.toString()+"</small>";
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(Html.fromHtml(message))
				.setCancelable(false)
				.setNegativeButton("Exit", terminateOnClick)
				.setPositiveButton("Continue", cancelOnClick)
		       .setTitle("Error");
		builder.show();
    }

    protected void showInfoDialog(String message) { showInfoDialog("Information", message, false); }
    protected void showInfoDialog(String title, String message) {
    	showInfoDialog(title, message, false);
    }
    protected void showInfoDialog(String title, String message, boolean asHTML) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		if (!asHTML) message = message.replace("\n", "<br>"); 
		final String fullMessage = message;
		builder.setMessage(Html.fromHtml("<small>"+message+"</small>"))
			.setPositiveButton("OK", cancelOnClick)
			.setNeutralButton("Copy", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setText(fullMessage); 
					dialog.cancel();
				}})
			.setTitle(title);
		builder.show(); 
    }

    public class OnLoginClickListener implements OnClickListener {
		public void onClick(DialogInterface dialog, int id) {
			Dialog d = (Dialog)dialog;
			onLoginClick(((EditText)d.findViewById(R.id.hostname)).getText().toString(), 
					((EditText)d.findViewById(R.id.password)).getText().toString());
			dialog.cancel();
		}
		public void onLoginClick(String user, String password) { }
    }

    public void showLoginDialog(String title, OnLoginClickListener onLogin) {
    	showLoginDialog(title, onLogin, null, false);
    }
    public void showLoginDialog(String title, OnLoginClickListener onLogin, String userName, boolean userNameDisabled) {
		AlertDialog.Builder builder;
		
		LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.login,
		                               (ViewGroup) findViewById(R.id.root));
		EditText hostName = ((EditText)layout.findViewById(R.id.hostname));
		if (null!=userName) hostName.setText(userName);
		builder = new AlertDialog.Builder(this);
		builder.setView(layout)
			.setCancelable(true) 
			.setPositiveButton("Login", onLogin)
			.setNegativeButton("Cancel", cancelOnClick)
			.setTitle(title);
		builder.show();
    }
    public String quoted(String in) { return "'"+in.replace("\'", "\\'")+"'"; }
}

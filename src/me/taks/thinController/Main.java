package me.taks.thinController;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jcraft.jsch.ChannelShell;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import me.taks.BaseActivity;

public class Main extends BaseActivity {
	SessionManager sessionManager;
	ChannelShell shell;
	ByteArrayOutputStream shellOutputStream;
	String shellOutput = "";
	String directory;
	PrintWriter shellInputWriter;
	LinearLayout directoryPicker;
	ListView fileList;
	Button fileManagerSlider;
	CheckBox showHiddenCheckbox;
	TextView shellOutputView;
	String[] subDirectories;
	String[] files;
	String[] executables;
	Vector<String> history;
	AutoCompleteTextView typeCommand;
	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
	        setContentView(R.layout.main);
			typeCommand = ((AutoCompleteTextView)findViewById(R.id.enterCommand));
	    	fileManagerSlider = ((Button)findViewById(R.id.slideHandleButton));
			directoryPicker = ((LinearLayout)findViewById(R.id.directoryPicker));
			shellOutputView = ((TextView)findViewById(R.id.outputPane));
			showHiddenCheckbox = ((CheckBox)findViewById(R.id.hiddenFilesCheckbox));
			showHiddenCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				public void onCheckedChanged(CompoundButton a, boolean b) { 
					try { drawFileList(); } 
					catch (Exception e) { dumpError(e); }
				}
			});
			fileList = ((ListView)findViewById(R.id.fileList));
			fileList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					try {
				    	String filename = ((TextView)view).getText().toString().trim();
				    	switch (((FileListAdapter)parent.getAdapter()).getType(filename)) {
				    	case SessionManager.DIRECTORY:
				    		setWorkingDirectory(Main.this.directory+filename);
				    		break;
				    	case SessionManager.EXECUTABLE:
				    		commandToInfoDialog("./"+filename);
				    		break;
				    	default:
				    		editFile(directory+filename); 
				    	}
					} catch (Exception e) { dumpError(e); }
			}});
			findViewById(R.id.showServices).setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) { showServicesDialog(); } });
			findViewById(R.id.phpProd).setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) { showPhpDialog("/hd/hd.c2/cli/"); } });
			findViewById(R.id.showHistory).setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) { showHistoryDialog(); } });
			if (sessionManager==null) {
		        OnLoginClickListener onLogin = new OnLoginClickListener() {
					public void onLoginClick(String user, String password){ doLogin(user, password); }
				};
		        showLoginDialog("Log into remote server", onLogin);
			}
        } catch (Exception e) { dumpError(e); }
   }
    
    public void launchRunThread() {
		Thread t = new Thread() { 
			public void run() { 
	        	while (Main.this.isTaskRoot()) {
	        		if (shellOutputStream.size() != shellOutput.length()) {
	        			shellOutput = shellOutputStream.toString();
//	        		if (true) {
//	        		shellOutput = Long.toString(System.currentTimeMillis());
	        			mHandler.post(mUpdateResults); 
	        		}
	        		try { sleep(20); } catch (Exception e) {}
	        	} 
        	}
		};
        t.start();
    }

    // Need handler for callbacks to the UI thread
    final Handler mHandler = new Handler();

    // Create runnable for posting
    final Runnable mUpdateResults = new Runnable() { 
    	public void run() { 
    		shellOutputView.setText(shellOutput); 
    		((ScrollView)shellOutputView.getParent()).smoothScrollTo(0, shellOutputView.getHeight());
    	} 
    };    
    
    
    private void buildCommandAutoComplete() throws Exception {
		String[] path=sessionManager.getCommandList();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(Main.this,
                android.R.layout.simple_dropdown_item_1line, path);
        typeCommand.setAdapter(adapter);
        typeCommand.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) 
                	switch (keyCode) {
                	case KeyEvent.KEYCODE_ENTER:
                		commandToInfoDialog(((EditText)v).getText().toString());
                		return true;
                	case KeyEvent.KEYCODE_SPACE:
                		showParamsDialog(((EditText)v).getText().toString(), new String[10], new String[10]);
                		return true;
                	default: return false;
                	} 
                else return false;
            }
        });
        typeCommand.setDropDownHeight(200);
        typeCommand.setDropDownVerticalOffset(-(typeCommand.getHeight()+typeCommand.getDropDownHeight()));
    }
    public void doLogin(String host, String password) {
		try { 
			sessionManager = SessionManager.get(host, password); 
			buildCommandAutoComplete();
			history = sessionManager.getHistory();
			shell = sessionManager.getShell();
		    shellOutputStream = new ByteArrayOutputStream();
		    shell.setOutputStream(shellOutputStream);
		    PipedOutputStream pos = new PipedOutputStream();
		    shell.setInputStream(new PipedInputStream(pos));
		    shellInputWriter = new PrintWriter(pos);
		    shell.connect();
			setWorkingDirectory();
//			shellInputWriter = new PrintWriter(new OutputStreamWriter(shell.getInputStream()));
		    launchRunThread();
		} catch (Exception e) { dumpError(e); }
    }

    public void setWorkingDirectory() throws Exception {
    	setWorkingDirectory(sessionManager.runSimpleCommand("pwd"));
    }
    class FileListAdapter extends ArrayAdapter<String> {
    	private SortedMap<String, Integer>list;
    	public int getType(String filename) { return list.containsKey(filename) ? list.get(filename) : 0; }
    	public FileListAdapter(Context cx, int layoutId, SortedMap<String, Integer>list) {
    		super (cx, layoutId, list.keySet().toArray(new String[list.size()]));
    		this.list = list;
    	}
    	public View getView(int position, View convertView, ViewGroup parent) {
    		TextView out = (TextView)(super.getView(position, convertView, parent));
    		switch (getType(out.getText().toString())) {
    		case SessionManager.DIRECTORY:
    			out.setCompoundDrawablesWithIntrinsicBounds(R.drawable.file_manager, 0, 0, 0);
    			break;
    		case SessionManager.EXECUTABLE:
    			out.setCompoundDrawablesWithIntrinsicBounds(R.drawable.bash, 0, 0, 0);
    			break;
    		default:
    			out.setCompoundDrawablesWithIntrinsicBounds(R.drawable.gedit_icon, 0, 0, 0);
    		}
    		out.setCompoundDrawablePadding(10);
    		return out;
    	}
    }
 
    public void setWorkingDirectory(String directory) {
		try {
	    	directory = directory.trim().replace("\n","");
	    	this.directory = directory+(directory.length()>1?"/":"");
	    	commandToInfoDialog("cd "+quoted(this.directory));
	    	directoryPicker.removeAllViews();
	    	int i;
	    	while ((i=directory.lastIndexOf('/'))>=0 && directory.length()>1) {
	    		Button b = new Button(this);
	    		b.setText(directory.substring(i+1));
	    		b.setId(directory.length());
	    		b.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) { 
					try {
						setWorkingDirectory(Main.this.directory.substring(0, v.getId()));
					} catch (Exception e) { dumpError(e); }
					}
				});
	    		directoryPicker.addView(b, 0);
	    		directory = directory.substring(0,i);
	    	}
			Button b = new Button(this);
			b.setText("/");
			b.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) { setWorkingDirectory("/"); }
			});
			directoryPicker.addView(b, 0);
			fileManagerSlider.setText(this.directory.substring(0, this.directory.length()-1));
			drawFileList();
		} catch (Exception e) { dumpError(e); }
    }

    public void drawFileList() throws Exception {
    	boolean showHidden = showHiddenCheckbox.isChecked();
		fileList.setAdapter(new FileListAdapter(this, R.layout.list_item, sessionManager.getFiles(this.directory, showHidden)));
    }

    public void commandToInfoDialog(String command) {
//	    doRunCommand("cd "+quoted(directory)+";"+quoted(command), new String[] {"1","2","3","4","5","6","7","8","9","10"});
    	history.add(0, command);
	    shellInputWriter.println(command);
	    shellInputWriter.flush();
    }

    public void editFile(String filename) {
    	try {
	    	Intent intent = new Intent(getApplication(), EditFile.class);
	    	Bundle b = new Bundle();
	    	b.putString("filename", filename);
	    	b.putString("host", sessionManager.host);
	    	b.putString("password", sessionManager.password);
	    	intent.putExtras(b);
	    	startActivityForResult(intent, 1);    	
    	} catch (Exception e) { dumpError(e); }
    }
    private String[] concat(String[] A, String[] B) {
    	TreeSet<String> v = new TreeSet<String>(Arrays.asList(A));
    	for (String b : B) v.add(b);
    	return v.toArray(new String[v.size()]);
	}

    protected void showServicesDialog() {
    	try {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setSingleChoiceItems(sessionManager.getServiceList(), -1, null)
				.setNegativeButton("Stop", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						ListView list = ((AlertDialog)dialog).getListView();
						int item = list.getCheckedItemPosition();
						if (item==AdapterView.INVALID_ROW_ID) return;
						dialog.cancel();
						commandToInfoDialog(quoted("/etc/init.d/"+(String)list.getAdapter().getItem(item))+" stop");
					}})
				.setNeutralButton("Restart", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						ListView list = ((AlertDialog)dialog).getListView();
						int item = list.getCheckedItemPosition();
						if (item==AdapterView.INVALID_ROW_ID) return;
						dialog.cancel();
						commandToInfoDialog(quoted("/etc/init.d/"+(String)list.getAdapter().getItem(item))+" restart");
					}})
				.setPositiveButton("Start", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						ListView list = ((AlertDialog)dialog).getListView();
						int item = list.getCheckedItemPosition();
						if (item==AdapterView.INVALID_ROW_ID) return;
						dialog.cancel();
						commandToInfoDialog(quoted("/etc/init.d/"+(String)list.getAdapter().getItem(item))+" start");
					}})
				.setTitle("Pick a service");
			builder.show(); 
    	} catch (Exception e) { dumpError(e); }
    }

    private File tempFile;
    public String getTempFileContents() throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(tempFile));
		String line;
		StringBuffer content = new StringBuffer();
		while (null!=(line=br.readLine())) content.append(line+"\n");
		return content.toString();
    }
    private String[] findPhpArgs(String filename) {
    	Vector<String> paramHints = 
    		new Vector<String>(Arrays.asList(new String[] {"1","2","3","4","5","6","7","8","9"}));
    	try {
	    	tempFile = File.createTempFile("fdphp", ".tmp");
	    	sessionManager.getScpFile(filename, tempFile);
	    	String content = getTempFileContents();
	    	Matcher m = Pattern.compile("\\$(\\w+)\\s*=\\s*\\$argv\\s*\\[\\s*([0-9]+)\\s*\\]").matcher(content);
	    	while (m.find()) paramHints.set(Integer.parseInt(m.group(2))-1, m.group(1));
	    	m = Pattern.compile("\\$(\\w+)\\s*=\\s*\\$_SERVER\\['argv'\\]\\s*\\[\\s*([0-9]+)\\s*\\]").matcher(content);
	    	while (m.find()) paramHints.set(Integer.parseInt(m.group(2))-1, m.group(1));
    	} catch (Exception e) { dumpError(e); return null; }
    	return paramHints.toArray(new String[paramHints.size()]);
    }
    protected void showPhpDialog(final String directory) {
    	try {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			final String[] files = concat(sessionManager.runSimpleCommand("cd "+quoted(directory)+"; find -name '*.php' -printf '%f\n'").split("\n"), new String[] {});
			builder.setItems(files, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
						commandToInfoDialog("pushd "+quoted(directory));
						String[] params = findPhpArgs(directory+files[id]);
						typeCommand.setText("php "+files[id]+" ");
						fileManagerSlider.performClick();
						showParamsDialog("php "+files[id], params, new String[10]);
					}})
				.setTitle("Pick a file to run");
			builder.show(); 
    	} catch (Exception e) { dumpError(e); }
    }

    protected void showHistoryDialog() {
    	try {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			final String[] historyArray = history.toArray(new String[history.size()]);
			builder.setItems(historyArray, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
						String command = historyArray[id];
						String[] params = new String[10];
						if (command.indexOf(' ')>0) {
							params = command.substring(command.indexOf(' ')+1).split(" ");
							command = command.substring(0,command.indexOf(' '));
						}
						typeCommand.setText(command);
						showParamsDialog(command, new String[10], params);
					}})
				.setTitle("Pick a command to run");
			builder.show(); 
    	} catch (Exception e) { dumpError(e); }
    }

    public void showParamsDialog(String command, String[] paramHints, String[] params) {
		AlertDialog.Builder builder;
		LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.params,
		                               (ViewGroup) findViewById(R.id.root));
		final EditText commandEdit = ((EditText)layout.findViewById(R.id.command));
		commandEdit.setText(command);
		TableLayout table = ((TableLayout)layout.findViewById(R.id.paramsTable));
		for (int i=0; i<paramHints.length; i++) {
			String paramHint = paramHints[i];
			String param = i<params.length?params[i]:"";
			TableRow tr = new TableRow(this);
			EditText label = new EditText(this);
			label.setHint(paramHint);
			label.setText(param);
			label.setWidth(LayoutParams.FILL_PARENT);
	        label.setOnKeyListener(new View.OnKeyListener() {
	            public boolean onKey(View v, int keyCode, KeyEvent event) {
	                if (event.getAction() == KeyEvent.ACTION_DOWN) 
	                	switch (keyCode) {
	                	case KeyEvent.KEYCODE_SPACE:
	                		v.clearFocus();
	                		return true;
	                	default: return false;
	                	} 
	                else return false;
	            }
	        });
			tr.addView(label);
			CheckBox cb = new CheckBox(this);
			cb.setWidth(LayoutParams.WRAP_CONTENT);
			cb.setFocusable(false);
			tr.addView(cb);
			table.addView(tr);
		}
		builder = new AlertDialog.Builder(this);
		builder.setView(layout)
			.setTitle("Enter Params")
			.setPositiveButton("Run", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					String text = commandEdit.getText().toString();
					TableLayout paramsTable = ((TableLayout)((Dialog)dialog).findViewById(R.id.paramsTable));
					for (int i=0;i<paramsTable.getChildCount();i++) {
						TableRow tr = ((TableRow)paramsTable.getChildAt(i));
						String param = ((EditText)(tr.getChildAt(0))).getText().toString();
						CheckBox cb = ((CheckBox)tr.getChildAt(1));
						text+=' '+(cb.isChecked() ? quoted(param) : param);
					}
					commandToInfoDialog(text);
					dialog.dismiss();
				}
			});
		builder.show();
    }

}
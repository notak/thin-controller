package me.taks.thinController;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import me.taks.BaseActivity;

public class EditFile extends BaseActivity {
	String filename;
	SessionManager sessionManager;
	EditText editPane;
	File tempFile;
	
    public void onCreate(Bundle savedInstanceState) {
        try {
	        super.onCreate(savedInstanceState);
	        setContentView(R.layout.edit_file);
	        editPane = (EditText)findViewById(R.id.editPane);
	        Bundle extras = getIntent().getExtras();
	        filename = extras.getString("filename");
	        //dumpError(new Exception(filename));
        	sessionManager = SessionManager.get(extras.getString("host"), extras.getString("password"));
        	editFile();
        	findViewById(R.id.saveChanges).setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					try {
						String content = editPane.getText().toString();
						if (getTempFileContents().equals(content))
							throw new Exception("File is unchanged");
						FileWriter fw = new FileWriter(tempFile);
						fw.write(content);
						fw.close();
						sessionManager.putScpFile(filename, tempFile);
					} catch (Exception e) { dumpError(e); }
				}
			});
        } catch (Exception e) { dumpError(e); }
    }
    public String getTempFileContents() throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(tempFile));
		String line;
		StringBuffer content = new StringBuffer();
		while (null!=(line=br.readLine())) content.append(line+"\n");
		return content.toString();
    }
    public void editFile() {
    	try {
    		tempFile = File.createTempFile("fdtc", ".tmp");
    		sessionManager.getScpFile(filename, tempFile);
//   		throw new Exception(content.toString());
		    editPane.setText(getTempFileContents());
	    } catch (Exception e) { dumpError(e); }
    }
}
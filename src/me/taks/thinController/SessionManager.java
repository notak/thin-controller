package me.taks.thinController;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class SessionManager {
	private static Hashtable<String, SessionManager> instances=new Hashtable<String, SessionManager>();
	
	public static SessionManager get (String host, String password) throws Exception {
		if (!instances.containsKey(host)) new SessionManager(host, password);
		SessionManager out = instances.get(host);
		out.connect();
		return out;
	}
	
	private JSch jsch=new JSch();
	public Session session;
	public String host;
	public String password;
	
	private SessionManager(String host, String password) throws Exception {
		this.host = host;
		this.password = password;
		String user=host.substring(0, host.indexOf('@'));
		String hostOnly=host.substring(host.indexOf('@')+1);
		session = jsch.getSession(user, hostOnly, 22);
		instances.put(host, this);
	}
	
	public void connect() throws Exception {
		if (!session.isConnected()) {
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");
		    session.connect();
		}
    }
	
	public String runSimpleCommand(String command) throws Exception {
		return runSimpleCommand(command, new String[] {});
	}
	public String runSimpleCommand(String command, String[] params) throws Exception {
	    Channel channel=session.openChannel("exec");
	    for (String param : params) if (null!=param) command+=" "+param;
	    //if (1==1) return command;
	    ((ChannelExec)channel).setCommand(command);
	    ByteArrayOutputStream os = new ByteArrayOutputStream();
	    channel.setOutputStream(os);
	    channel.connect();
/*		    InputStream in=channel.getInputStream();
		    byte[] tmp=new byte[4096];
		    channel.connect();
	        while(true){
	            while(in.available()>0){
	              int i=in.read(tmp, 0, 1024);
	              if(i<0)break;
	              System.out.print(new String(tmp, 0, i));
	            }
	            if(channel.isClosed()){
	              System.out.println("exit-status: "+channel.getExitStatus());
	              break;
	            }
	            try{Thread.sleep(1000);}catch(Exception ee){}
	          }
*/
	    while(true) {
            if(channel.isClosed()) break;
            try{Thread.sleep(10);}catch(Exception ee){}
	    }
	    channel.disconnect();   
	    //channel.getExitStatus()
	    return os.toString();
	}
	public ChannelShell getShell() throws Exception {
	    Channel channel=session.openChannel("shell");
	    //if (1==1) return command;
	    ((ChannelShell)channel).setPtyType("vt102");
	    return (ChannelShell)channel;
	}
	
	private static final String CMD_LIST_EXECS = "find -type f -maxdepth 1 -executable -printf '%f\n'";
	private static final String CMD_LIST_SUBDIRS = "find -type d -maxdepth 1 -printf '%f\n'";
		
    public Vector<String> getHistory() throws Exception {
    	Vector<String> out = new Vector<String>();
		for (String s: runSimpleCommand("cat ~/.bash_history").split("\n")) out.add(0, s);
		return out;
    }
    
    public String[] getCommandList() throws Exception {
		String[] path=new String[] {};
		for (String s: runSimpleCommand("echo $PATH").split(":"))
			path = concat(path, runSimpleCommand("cd "+quoted(s)+";" + CMD_LIST_EXECS).split("\n"));
		return path;
    }
    
    public String[] getServiceList() throws Exception {
		String files = runSimpleCommand("cd /etc/init.d;" + CMD_LIST_EXECS);
		return concat(files.split("\n"), new String[]{});
    }
    public static final int FILE = 1;
    public static final int DIRECTORY = 2;
    public static final int EXECUTABLE = 4;
    
    public TreeMap<String, Integer> getFiles(String directory, boolean includeHidden) throws Exception {
    	TreeMap<String, Integer> files = new TreeMap<String, Integer>();
    	String cmdCd = "cd "+quoted(directory)+";";
		for (String filename : runSimpleCommand(cmdCd+"find -type f -maxdepth 1 -printf '%f\n'").split("\n"))
			if (filename.length()>0 && (includeHidden || filename.charAt(0)!='.'))
				files.put(filename, FILE);
		for (String filename : runSimpleCommand(cmdCd+CMD_LIST_EXECS).split("\n"))
			if (filename.length()>0 && (includeHidden || filename.charAt(0)!='.'))
				files.put(filename, EXECUTABLE);
		for (String filename : runSimpleCommand(cmdCd+CMD_LIST_SUBDIRS).split("\n"))
			if (filename.length()>0 && (includeHidden || filename.charAt(0)!='.'))
				files.put(filename, DIRECTORY);
		return files;
    }
    
    private String[] concat(String[] A, String[] B) {
    	TreeSet<String> v = new TreeSet<String>(Arrays.asList(A));
    	for (String b : B) v.add(b);
    	return v.toArray(new String[v.size()]);
	}
    private String quoted(String in) { return "'"+in.replace("\'", "\\'")+"'"; }
	public void getScpFile(String filename, File outfile) throws Exception {
		String command="scp -f "+quoted(filename);
	    Channel channel=session.openChannel("exec");
	    ((ChannelExec)channel).setCommand(command);
		OutputStream out=channel.getOutputStream();
		InputStream in=channel.getInputStream();
	    FileOutputStream fos=null;
		channel.connect();

		byte[] buf=new byte[1024];
		// send '\0'
		buf[0]=0; out.write(buf, 0, 1); out.flush();
		long filesize=0L;
		while(true){
			int c=checkAck(in);
			if(c!='C') break;
			
			for (int i=0; i<5; i++) in.read(); // read '0644 '
			
			filesize=0L;
			while(true){
				int byteIn;
				if((byteIn=in.read())<0) break; //error
				if(byteIn==' ')break;
				filesize=filesize*10L+(long)(byteIn-'0');
			}
			
			String file=null;
			for(int i=0;;i++){
				in.read(buf, i, 1);
				if(buf[i]==(byte)0x0a){
					file=new String(buf, 0, i);
					break;
				}
			}
			if (!file.equals(filename.substring(filename.lastIndexOf("/")+1)))
				throw new Exception ("Unexpected file "+file+ "returned when requesting "+filename);
			
			// send '\0'
			buf[0]=0; out.write(buf, 0, 1); out.flush();
			fos=new FileOutputStream(outfile);
			int foo;
			while(true){
				if(buf.length<filesize) foo=buf.length; else foo=(int)filesize;
				foo=in.read(buf, 0, foo);
				if(foo<0) break; //error
			    fos.write(buf, 0, foo);
				filesize-=foo;
				if(filesize==0L) break;
			}
			fos.close();
			fos=null;
			
			int ack = checkAck(in);
			if(ack!=0) {
				channel.disconnect();
				throw new Exception("Ack failed: "+ack);
			}
			
			buf[0]=0; out.write(buf, 0, 1); out.flush(); // send '\0'
		}
		channel.disconnect();
//		if (1==1) throw new Exception("filesize="+filesize+", file="+outfile.getName());
	}

	static int checkAck(InputStream in) throws IOException {
        int b=in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if(b==0) return b;
        if(b==-1) return b;

        if(b==1 || b==2){
          StringBuffer sb=new StringBuffer();
          int c;
          do {
    	c=in.read();
    	sb.append((char)c);
          }
          while(c!='\n');
          if(b==1){ // error
    	System.out.print(sb.toString());
          }
          if(b==2){ // fatal error
    	System.out.print(sb.toString());
          }
        }
        return b;
      }
	
	public void putScpFile(String filename, File infile) throws Exception {
		String command="scp -p -t "+quoted(filename);
	    Channel channel=session.openChannel("exec");
	    ((ChannelExec)channel).setCommand(command);
		OutputStream out=channel.getOutputStream();
		InputStream in=channel.getInputStream();
		channel.connect();

		if(checkAck(in)!=0) throw new Exception("failed to handshake writing file");

		// send "C0644 filesize filename", where filename should not include '/'
		long filesize=infile.length();
		command="C0644 "+filesize+" ";
		command+=filename.substring(filename.lastIndexOf('/')+1); //filename part only
		command+="\n";
		out.write(command.getBytes()); out.flush();
		if(checkAck(in)!=0) throw new Exception("failed to handshake writing file");

		// send a content of lfile
		FileInputStream fis=new FileInputStream(infile);
		byte[] buf=new byte[1024];
		while(true){
			int len=fis.read(buf, 0, buf.length);
			if(len<=0) break;
		    out.write(buf, 0, len); //out.flush();
		}
		fis.close();
		fis=null;
		
		// send '\0'
		out.write(0); out.flush();
		
		if(checkAck(in)!=0) throw new Exception("failed to handshake writing file");
		
		out.close();
		channel.disconnect();
	}
}
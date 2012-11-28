package com.sysdream.gadget;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class GadgetService extends Service implements IGadgetService {

	private static ServerSocket server = null;
	public GadgetServiceBinder binder = null;
	private static ServerThread server_thread = null;
	
	public class ClientThread extends Thread {

		private final static String TAG ="CLIENT";
		private Socket client = null;
		private BufferedReader sock_in = null;
		private BufferedWriter sock_out = null;
		private char[] size_buf = new char[4];
		private int size = 0;
		private int msg_type = 0;
		private boolean m_running = false;
		private ServerThread m_parent = null;
		
		public ClientThread(Socket client, ServerThread parent) {
			this.client = client;
			this.m_parent = parent;
			this.m_running = true;
		}

		public synchronized boolean isRunning() {
			return m_running;
		}

		public synchronized void kill() {
			m_running = false;
			try {
				this.client.close();
			}
			catch (IOException sockerr) {
			}
			this.interrupt();
		}

		
		private void readMessage(int msg_type, int size) throws IOException {
			int nbread = -1;
			char[]raw_json = new char[size];
			if (this.sock_in.read(raw_json, 0, size) == size)
			{
				/* Build the corresponding message based on the serialized data */
				Request req = Request.fromJson(new String(raw_json));
				Log.d(TAG, "Got request: "+req.getMethod().toString());
				Log.d(TAG, "Intval: "+String.valueOf(req.getParameters().intval));
			}
		}
		
		public void run() {  
			int nbread = -1;
        	try {
        		Log.d(TAG, "Handle client connection");
        		this.sock_in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        		this.sock_out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
                while (this.isRunning()) {
                	nbread = this.sock_in.read(size_buf, 0, 4);
            		if (nbread == 4)
            		{
            			/* Convert size bytes to real size int */
            			size = ByteBuffer.wrap(new String(size_buf).getBytes()).getInt();
            			/* Process message */
            			this.readMessage(0, size);
            		}
            		else if (nbread < 0)
            			break;
                }
                this.client.close();
                Log.d(TAG, "Client disconnected");
                this.m_parent.onClientDisconnect(this);
        	} catch(SocketException sockerr) {
        		Log.d(TAG, "Client socket closed");
        		this.m_parent.onClientDisconnect(this);
        	}
        	catch(Exception e) {
        		e.printStackTrace();
        	}
        }       

	}
	
	public class ServerThread extends Thread {

		private int port = -1;
		private boolean m_running = false;
		private ServerSocket server = null;
		private ArrayList<ClientThread> m_clients = new ArrayList<ClientThread>();

		@Override
		public void start() {
			super.start();
			m_running = true;
		}
		
		public synchronized void kill() {
			m_running = false;
			try {
				/* Kill all clients */
				for (ClientThread client : m_clients)
					client.kill();
				this.server.close();
			}
			catch (IOException sockerr) {
			}
			this.interrupt();
		}
		
		public synchronized boolean isRunning() {
			return this.m_running;
		}
		
	    public ServerThread(int port) {
	    	this.port = port;
	    }
		
	    public void run() {
	    	ClientThread client = null;
	    	
	         try {
	             this.server = new ServerSocket(this.port);
	             while (this.isRunning()) {
	            	 Socket client_sock = this.server.accept();
	            	 client = new ClientThread(client_sock, this);
	            	 m_clients.add(client);
	            	 client.start();
	             }
	             
	         }
	         catch (SocketException e)
	         {
	        	 Log.d("Service", "Service socket closed");
	         }
	         catch (Exception e) {
	             e.printStackTrace();
	         }
	    }
	    
	    public void onClientDisconnect(ClientThread client) {
	    	if (m_clients.contains(client))
	    		m_clients.remove(client);
	    }
	}
	
	public class GadgetServiceBinder extends Binder {
		private IGadgetService service = null;
		
		public GadgetServiceBinder(IGadgetService service) {
			this.service = service;
		}
		
		public void startServer(String address, int port, int mode){
			if (service != null)
				service.startServer(address, port, mode);
		}
		
		public void stopServer() {
			if (service != null)
				service.stopServer();
		}
		
		public int getMode() {
			if (service != null)
				return service.getMode();
			return -1;
		}
		
		public String getAddress() {
			if (service != null)
				return service.getAddress();
			return null;
		}
		
		public int getPort() {
			if (service != null)
				return service.getPort();
			return -1;
		}
		
		public IGadgetService getService() {
			return service;
		}
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		this.binder = new GadgetServiceBinder(this); 
	}
	
	public void OnDestroy() {
		super.onDestroy();
		Log.d("Service", "Service destroyed");
	}

	public void startServer(String address, int port, int mode) {
		if (this.server_thread == null)
		{
			Log.d("Service", "server_thread == null");
			this.server_thread = new ServerThread(port);
			this.server_thread.start();
		}
	}

	public void stopServer() {
		Log.d("Service", "Stop service");
		if (this.server_thread != null)
			this.server_thread.kill();
		this.server_thread = null;
	}


	public int getMode() {
		// TODO Auto-generated method stub
		return 0;
	}


	public String getAddress() {
		// TODO Auto-generated method stub
		return null;
	}


	public int getPort() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return this.binder;
	}
}


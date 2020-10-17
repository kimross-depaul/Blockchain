import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.UUID;

/*
 * Classes I'll need
 * 	The BlockChainServer (has to be called Blockchain.java)
 * 	The KeyWorker
 * 	The UnverifiedBlockWorker
 * 	The VerifiedBlockWorker
 * 	The MedBlock class
 */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.net.*;
import java.util.ArrayList;

/*
 * ***************************************************************************
***************************** REQUESTED HEADER *****************************
****************************************************************************
*
*	1.  NAME:  			Kim Ross 	DATE:  9/20/2020
*	2.  JAVA VERSION:  	Java SE-9 (from JDK 11.0.5)
*	3.  RUN INSTRUCTIONS:
*
*		This application is comprised of 3 highly-coupled classes:  
*			JokeServer.java 
*			JokeClient.java 
*			JokeClientAdmin.java
*
*		The JokeClient connects to the JokeServer to retrieve a Joke or a Proverb
*		The JokeClientAdmin toggles the JokeServer's mode between  "Joke" and "Proverb" modes
*		The JokeServer provides Jokes and Proverbs to the JokeClient
*
*		Per the assignment specifications, the JokeServer listens for 
*			JokeClients on port 4545
*			JokeClientAdmins on port 5050
*		With an additional command line parameter, the Joke Server also listens for
*			JokeClients on port 4546
*			JokeClientAdmins on port 5051
*		
*		To Compile:  
*		javac JokeServer.java
*
*		To Run:
*		In separate command-line windows, run 1 or 2 servers and as many JokeClients
*		and JokeAdminClients as you like.
*
*			RUNNING THE SERVER:
*			java JokeServer 
*			
*			RUNNING A SECONDARY SERVER
*			java JokeServer secondary		
*		
*	4.  EXAMPLES 
*		Run each of these commmands in their own Command / Shell windows:
*
*		java JokeServer
*
*		java JokeServer secondary
*		
*		java JokeClient localhost
*
*		java JokeClient localhost localhost
*
*		java JokeClientAdmin localhost
*
*		java JokeClientAdmin localhost localhost
*
* 	5.   NOTES:
* 		
* 	The base code for this project was adapted from Clark Elliot's InetServer
*/


public class BlockChain extends Thread {

	public static void main(String[] args) throws InterruptedException {
		//Read data from text file
		String ProcessID = "0";
		if (args.length > 0) ProcessID = args[0];
		//PriorityQueue<MedicalBlock> ub = readBlocksFromFile("BlockInput" + ProcessID + ".txt");
		//PriorityQueue<MedicalBlock> blockChain = new PriorityQueue<MedicalBlock>();
		
		//Create a ModeHolder and 2 servers:  the JokeServer (server) and the AdminServer
		BlockHolder blockHolder = new BlockHolder();
		BlockServer UBserver = new BlockServer(4820, ProcessID, new UnverifiedBlockWorker(blockHolder), blockHolder);
		BlockServer blockChainServer = new BlockServer(4930, ProcessID, new VerifiedBlockWorker(blockHolder), blockHolder);
		
		UBserver.start();
		blockChainServer.start();
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		sendMessageToServer("here's a message", 48201);
		sendMessageToServer(gson.toJson(blockHolder.ub) + "\n\r", 49301);
		sendMessageToServer("here's a message", 48201);
		sendMessageToServer(gson.toJson(blockHolder.ub) + "\n\r", 49301);
		sendMessageToServer("here's a message", 48201);
		sendMessageToServer(gson.toJson(blockHolder.ub) + "\n\r", 49301);
		sendMessageToServer("here's a message", 48201);
		sendMessageToServer(gson.toJson(blockHolder.ub) + "\n\r", 49301);
		sendMessageToServer("here's a message", 48201);
		sendMessageToServer(gson.toJson(blockHolder.ub) + "\n\r", 49301);
		sendMessageToServer("here's a message", 48201);
	}
	public static void sendMessageToServer(String msg, int port) {
		Socket socket;					//Generic connection between 2 hosts
		BufferedReader fromServer;		//We need to be able to read data from the server
		PrintStream toServer;			//We need to be able to send data to the server
		String textFromServer;
		
		try {
			//Connect to the server and establish handles for reading and writing
			socket = new Socket("localhost", port);	
			//fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			toServer = new PrintStream(socket.getOutputStream());
			
			toServer.print(msg); 
			toServer.flush();
			//System.out.println("\tsent " + msg);
		
			//Close the socket, we're done.
			socket.close();
			
		}catch (IOException iox) {
			//Reading and Writing can throw an IOException, which we have to handle
			System.out.println("Error reading from server: " + iox.getMessage());
		}
	}
	

}
class BlockHolder {
	PriorityQueue ub;
	PriorityQueue blockChain;
	
	public BlockHolder() {}
	
	public void readBlocksFromFile(String fileName) {
		ArrayList<String> ret = new ArrayList<String>();
		Scanner scanner = null;
		try {
			java.io.File file = new java.io.File(System.getProperty("user.dir") + "\\src\\" + fileName);
			scanner = new Scanner(file);
    	
			while (scanner.hasNextLine()) {
				ret.add(scanner.nextLine());
			}
				
		} catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
		} finally {
			if (scanner != null) scanner.close();
		}
		this.ub = new PriorityQueue<MedicalBlock>();
		for (int i = 0; i < ret.size(); i++) {
			ub.add(new MedicalBlock(ret.get(i)));
		}
	}
}
//This accepts either a worker that serves Jokes/Proverbs or a worker that toggles nodes
class BlockServer extends Thread {
	int port;					
	Socket socket;
	ServerSocket serverSocket;
	IWorker worker;
	boolean run = true;			//We keep running until run is false
	BlockHolder blockHolder;
	
	public BlockServer(int _port, String ProcessID, IWorker _worker, BlockHolder _blockHolder) {	
		this.port = Integer.valueOf(_port + ProcessID);
		this.blockHolder = _blockHolder;
		if (blockHolder.ub == null) this.blockHolder.readBlocksFromFile("BlockInput" + ProcessID + ".txt");
		
		try {
			this.serverSocket = new ServerSocket(port, 6);
			this.worker = _worker;
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Kim Ross's version of JokeServer, adapted from Clark Elliot's Inet server 1.1 - listening on port " + port);
	}	

	public void run() {
		//Keep running until the JokeClientAdmin tells us to shut down.
		//This just continually accepts requests
		//the Worker processes the input.
		while (true) { //modeHolder.shouldRun()) {
			try {
				System.out.print("<");
				socket = serverSocket.accept();
				worker.setSocket(socket);
				worker.run();
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}	
		}
		//Housekeeping
		/*
		try {
			socket.close();
		} catch (NullPointerException nullex) {
			System.out.println("Couldn't close the socket, it wasn't initialized yet.");
		} catch (IOException e) {
			e.printStackTrace();
		}*/
	}
}

/*
 * Both JokeServer instances needed a "Worker"
 * But each worker needed to do different things.
 * An interface lets each kind of server has it's own worker by composition
 */
interface IWorker  {
	public void start();
	public void run();
	public void setSocket(Socket _socket);
}

class UnverifiedBlockWorker extends Thread implements IWorker {
	Socket socket;
    BlockHolder blockHolder;
	
	UnverifiedBlockWorker (BlockHolder _blockHolder) { //ModeHolder _modeHolder) {
		this.blockHolder = _blockHolder;
	}
	
	@Override
	public void setSocket(Socket _socket) {
		this.socket = _socket;
	}
	
	public void run() {
		PrintStream out = null;
		BufferedReader in = null;
		try {
			//"socket" gives us 2 streams:
			//	- One for incoming data
			//	- One for outgoing data
			in = new BufferedReader (new InputStreamReader(socket.getInputStream()));
			out = new PrintStream(socket.getOutputStream());
			
			try {
				//Get the Joke/Proverb our client is requesting
				StringBuilder sb = new StringBuilder();
				String result = "";
					
				System.out.println("-----------------" + in.ready() + "--------------------------");
				while ((result = in.readLine()) != null) {
					System.out.println("\t" + result);
					sb.append(result);
				}
					
				System.out.println("\tUBlockworker Recieved item:  " + sb.toString());
				
				//And send it back to our JokeClient
				out.println("I got this from you:  " + sb.toString());
			}catch (NumberFormatException numex) {
				System.out.println("Server received an invalid joke/proverb index");
			}catch (IOException iox) {
				//If we're here, we couldn't read incoming data from the client
				System.out.println("Server encountered a read-error: " + iox.getMessage());
			}
			socket.close();
			System.out.print(">");
		}catch (IOException ioe) {
			//Accessing the stream in either direction, in or out, can throw an IOException
			System.out.println("Server encountered a read or write error:  " + ioe.getMessage());
		}
	}
}

class VerifiedBlockWorker extends Thread implements IWorker {
	Socket socket;
    BlockHolder blockHolder;
	
	VerifiedBlockWorker (BlockHolder _blockHolder) { //ModeHolder _modeHolder) {
		this.blockHolder = _blockHolder;
	}
	
	@Override
	public void setSocket(Socket _socket) {
		this.socket = _socket;
	}
	
	public void run() {
		PrintStream out = null;
		BufferedReader in = null;
		try {
			//"socket" gives us 2 streams:
			//	- One for incoming data
			//	- One for outgoing data
			in = new BufferedReader (new InputStreamReader(socket.getInputStream()));
			out = new PrintStream(socket.getOutputStream());
			
			try {
				//Get the Joke/Proverb our client is requesting
				StringBuilder sb = new StringBuilder();
				String result = "";

				System.out.println("-----------------bc" + in.ready() + "--------------------------");				
				while ((result = in.readLine()) != null) {
					result = in.readLine();
					sb.append(result);
				}
				/*while ((result = in.readLine()) != null) {
					sb.append(result);
				}  */
					
				System.out.println("\tBlockchain Worker Recieved item:  " + sb.toString());
				
				//And send it back to our JokeClient
				out.println("I got this from you:  " + sb.toString());
			}catch (NumberFormatException numex) {
				System.out.println("Server received an invalid joke/proverb index");
			}catch (IOException iox) {
				//If we're here, we couldn't read incoming data from the client
				System.out.println("Server encountered a read-error: " + iox.getMessage());
			}
			socket.close();
		}catch (IOException ioe) {
			//Accessing the stream in either direction, in or out, can throw an IOException
			System.out.println("Server encountered a read or write error:  " + ioe.getMessage());
		}
	}
}

class MedicalBlock implements Comparable {
	  String BlockID;
	  String VerificationProcessID;
	  String PreviousHash; 
	  String Fname;
	  String Lname;
	  String DOB;
	  String SSNum;
	  String Diag;
	  String Treat;
	  String Rx;
///	  String RandomSeed; // Our guess. Ultimately our winning guess.
	  String WinningHash;
	  Date timeStamp;
	  
	//John Smith 1996.03.07 123-45-6789 Chickenpox BedRest aspirin

	  public MedicalBlock(String fileInput) {
		  String[] vals = fileInput.split(" ");
		  BlockID = UUID.randomUUID().toString();
		  Fname = vals[0];
		  Lname = vals[1];
		  DOB = vals[2];
		  SSNum = vals[3];
		  Diag = vals[4];
		  Treat = vals[5];
		  Rx = vals[6];
		  timeStamp = new Date();
	  }
	  
	  
	  public String toJson() {
		  GsonBuilder gb = new GsonBuilder();
		  gb.setPrettyPrinting();
		  Gson gson = gb.create();
		  return gson.toJson(this);
	  }

	@Override
	public int compareTo(Object o) {
		MedicalBlock m = (MedicalBlock)o;
		if (m.timeStamp.before(this.timeStamp)) {
			return -1;
		}else if (m.timeStamp.after(this.timeStamp)) {
			return 1;
		}else {
			return 0; //must be the same?
		}
		
	}
}	

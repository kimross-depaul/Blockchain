import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
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
 * IN MY OWN WORDS
- You have 3 servers:  p0, p1, p2
x Each server reads its own BlockInputN.Txt file
x Each server creates UnverifiedBlocks from its text file
o Each server multicasts its unverified blocks to the other servers (listening on 4820+processID)
- Each server listens for the multi-casted UB's and adds them to its own PriorityQueue of UBs
- Each server goes to work on the highest-priority block
- First solver stamps the solved UB (proved work) and multicasts just that VB to the other servers
- Each server updates its own local copy of the BlockChain (listening on 4930+processID)
- Server 0 writes it to the log file.

what datatype is his timestamp
	date = new Date();
	T1 = String.format("%1$s %2$tF.%2$tT", "", date); // Create the TimeStamp string.
	TimeStampString = T1 + "." + i; // Use process num extension. No timestamp collisions!
he had 
	a server for keys
	a server for getting incoming unverified blocks
	a server to consume queued-up unverified blocks

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
	public static int NUM_CONSORTIUM_MEMBERS = 3;
	public static String KEY_PORT_PREFIX = "471";
	public static String UB_PORT_PREFIX = "482";
	public static String BLOCK_PORT_PREFIX = "493";

	public static void main(String[] args) throws InterruptedException {
		//Read data from text file
		String ProcessID = "0";
		if (args.length > 0) ProcessID = args[0];
		int intProcessID = Integer.valueOf(ProcessID);
		System.out.println("I'm process " + ProcessID);
		
		//Create a ModeHolder and 2 servers:  the JokeServer (server) and the AdminServer
		BlockHolder blockHolder = new BlockHolder(NUM_CONSORTIUM_MEMBERS);
		BlockServer keyServer = new BlockServer(parseTargetPort(KEY_PORT_PREFIX, intProcessID), ProcessID, new KeyWorker(blockHolder), blockHolder);
		BlockServer UBserver = new BlockServer(parseTargetPort(UB_PORT_PREFIX, intProcessID), ProcessID, new UnverifiedBlockWorker(blockHolder), blockHolder);
		BlockServer blockChainServer = new BlockServer(parseTargetPort(BLOCK_PORT_PREFIX, intProcessID), ProcessID, new VerifiedBlockWorker(blockHolder), blockHolder);
		
		keyServer.start();
		UBserver.start();
		//blockChainServer.start();	
		
		//port 471X receives public keys
		//port 482X receives unverified blocks
		//port 493X receives verified blocks
		
		//1.  Send my key
		sendKeys(ProcessID);
		
		//2.  Only process #2 checks to see if we got all the keys.
		while (!blockHolder.hasAllKeys() && intProcessID == NUM_CONSORTIUM_MEMBERS -1) {
			System.out.println("... collecting the keys from the consortium...");
			Thread.sleep(2000);
		}
		if (blockHolder.hasAllKeys()) {
			System.out.println("All Keys Received.");
			blockHolder.printKeys();
		}
		
		//2.  When we have all the keys, send/receive UBs
		sendMyUBs(ProcessID, blockHolder);
		
		//3.  Start working on blocks, end out when you solve one
		//GET THE FIRST BLOCK AND START DOING WORK
		
		//I SOLVED IT (AM I THE FIRST? SEE IF ITS ALREADY IN THE BLOCKCHAIN)
		
		//I AM FIRST!  PROCESS THE BLOCK...
		
		
		//...AND PUBLISH IT TO THE CONSORTIUM
		//sendMessageToServer("here's a message", parseTargetPort("493", 1));
	}
	private static int parseTargetPort(String portPrefix, int processId) {
		String tempPort = portPrefix + processId;
		return Integer.valueOf(tempPort);
	}
	
	public static void sendKeys(String myProcessID) {
		//I THINK I SHOULD SEND THS AS JSON AND CONSUME IT ON THE OTHER END AS A PublicKey
		PublicKey myKey = KeyUtils.getMyPublicKey(Integer.valueOf(myProcessID));
		for (int i = 0; i < NUM_CONSORTIUM_MEMBERS; i++) {	
			sendMessageToServer(myProcessID + myKey.toString(), parseTargetPort(KEY_PORT_PREFIX, i));
		}		
	}
	public static void sendMyUBs(String myProcessID, BlockHolder blockHolder) {
		PriorityQueue<MedicalBlock> fileUBs = readBlocksFromFile("BlockInput" + myProcessID + ".txt");
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		for (int i = 0; i < NUM_CONSORTIUM_MEMBERS; i++) {	
			sendMessageToServer(gson.toJson(fileUBs), parseTargetPort(UB_PORT_PREFIX, i));
		}		
	}
	public static PriorityQueue<MedicalBlock> readBlocksFromFile(String fileName) {
		ArrayList<String> ret = new ArrayList<String>();
		Scanner scanner = null;
		try {
			java.io.File file = new java.io.File(System.getProperty("user.dir") + "\\" + fileName);
			System.out.println("Reading blocks from text file " + file.getAbsolutePath());
			scanner = new Scanner(file);
    	
			while (scanner.hasNextLine()) {
				ret.add(scanner.nextLine());
			}
				
		} catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
		} finally {
			if (scanner != null) scanner.close();
		}
		PriorityQueue<MedicalBlock> ub = new PriorityQueue<MedicalBlock>();

		for (int i = 0; i < ret.size(); i++) {
			ub.add(new MedicalBlock(ret.get(i)));
		}
		return ub;
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
class KeyUtils {
	 //Taken from BlockJ.java sample code
	/*need to figure out where the credit belong... The web sources:

		https://mkyong.com/java/how-to-parse-json-with-gson/
		http://www.java2s.com/Code/Java/Security/SignatureSignAndVerify.htm
		https://www.mkyong.com/java/java-digital-signatures-example/ (not so clear)
		https://javadigest.wordpress.com/2012/08/26/rsa-encryption-example/
		https://www.programcreek.com/java-api-examples/index.php?api=java.security.SecureRandom
		https://www.mkyong.com/java/java-sha-hashing-example/
		https://stackoverflow.com/questions/19818550/java-retrieve-the-actual-value-of-the-public-key-from-the-keypair-object
		https://www.java67.com/2014/10/how-to-pad-numbers-with-leading-zeroes-in-Java-example.html
	*/

	public static PublicKey getMyPublicKey(long seed) {
		KeyPair keyPair = generateKeyPair(seed);
		return keyPair.getPublic();
	}
	//Makes a key pair so I can extract the public key
	public static KeyPair generateKeyPair(long seed)  {
	    KeyPairGenerator keyGenerator = null;
		try {
			keyGenerator = KeyPairGenerator.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			System.out.println(e.getMessage());
		}
	    SecureRandom rng = null;
		try {
			rng = SecureRandom.getInstance("SHA1PRNG", "SUN");
		} catch (NoSuchAlgorithmException | NoSuchProviderException e) {
			System.out.println(e.getMessage());
		}
	    rng.setSeed(seed);
	    keyGenerator.initialize(1024, rng);
	    
	    return (keyGenerator.generateKeyPair());
	}
}
class BlockHolder {
	PriorityQueue<MedicalBlock> ub;
	PriorityQueue<MedicalBlock> blockChain;
	ArrayList<String> consortiumKeys;
	int numConsortiumMembers;
	int keysReceived;
	
	public BlockHolder(int _numConsortiumMembers) {
		consortiumKeys = new ArrayList<String>(_numConsortiumMembers);
		numConsortiumMembers = _numConsortiumMembers;
		keysReceived = 0;
		ub = new PriorityQueue<MedicalBlock>();
		
		//The arraylist didn't like being populated out of order, so let's initialize each first
		for (int i = 0; i < numConsortiumMembers; i++) {
			consortiumKeys.add(i, "");
		}
		
	}
	public void addKey(String key, String processId) {
		System.out.println("\tadding key " + key + " to position " + processId);
		consortiumKeys.set(Integer.valueOf(processId), key);
		keysReceived ++;
	}
	public String getKey(int pos) {
		return (String) consortiumKeys.get(pos);
	}
	public boolean hasAllKeys() {
		return keysReceived == numConsortiumMembers;
	}
		
	public void printKeys() {
		for (int i = 0; i < consortiumKeys.size(); i++) {
			System.out.println(i + ":  " + consortiumKeys.get(i));
		}
	}
	public void printUb() {
		Iterator it = ub.iterator();
		while (it.hasNext()) {
			MedicalBlock b = (MedicalBlock)it.next();
			System.out.println("==" + b.Fname + " " + b.Lname);
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
		this.port = Integer.valueOf(_port);// + ProcessID);
		this.blockHolder = _blockHolder;
		
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
class KeyWorker extends Thread implements IWorker {
	Socket socket;
	BlockHolder blockHolder;
	
	KeyWorker (BlockHolder _blockHolder) {
		this.blockHolder = _blockHolder;
	}
	@Override
	public void setSocket(Socket _socket) {
		this.socket = _socket;
	}
	
	public void run() {
		PrintStream out = null;
		BufferedReader in = null;
		String whatFailed = "";
		try {
			//"socket" gives us 2 streams:
			//	- One for incoming data
			//	- One for outgoing data
			in = new BufferedReader (new InputStreamReader(socket.getInputStream()));
			out = new PrintStream(socket.getOutputStream());
			
			try {
				//Get the key someone is sending us
				StringBuilder sb = new StringBuilder();
				String result = "";
				
				//READING KEYS FROM CONSORTIUM MEMBERS
				while ((result = in.readLine()) != null) {
					System.out.println("\t" + result);
					sb.append(result);
				}					
			
				String key = "";
				String processId = "";
				whatFailed = sb.toString(); //if this fails, I want to see what it was.
				//They'll send us 1 digit for their process ID and the rest is their key.
				if (sb.toString().length() > 0) {
					processId = sb.toString().substring(0,1);
					key = sb.toString().substring(1, sb.toString().length());
				}
				
				//ADDING THE KEY TO OUR KEY-ARRAY
				blockHolder.addKey(key, processId);
			}catch (NumberFormatException numex) {
				System.out.println("Server received an invalid key from a consortium member: {" + whatFailed + "} on port " + this.socket.getLocalPort());
			}catch (IOException iox) {
				//If we're here, we couldn't read incoming data from the client
				System.out.println("Key Server encountered a read-error: " + iox.getMessage());
			}
			socket.close();
			System.out.print(">");
		}catch (IOException ioe) {
			//Accessing the stream in either direction, in or out, can throw an IOException
			System.out.println("Server encountered a read or write error:  " + ioe.getMessage());
		}
	}
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
				
				//READING VERIFIED AND UNVERIFIED BLOCKS FROM THE CONSORTIUM
				System.out.println("-----------------" + in.ready() + "--------------------------");
				while ((result = in.readLine()) != null) {
					System.out.println("\t" + result);
					sb.append(result);
				}					
				
				//ADDING UNVERIFIED BLOCKS TO THE UB CHAIN
				updateUnverifiedBlocks(sb.toString());
				
				this.blockHolder.printUb();
				
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
	private void updateUnverifiedBlocks(String json) {
		//GSON needs to know what type of object to return
		java.lang.reflect.Type ubArray = new TypeToken<ArrayList<MedicalBlock>>() {}.getType();
		
		//Convert the json string into an ArrayList
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		ArrayList<MedicalBlock> incomingUBs = gson.fromJson(json, ubArray);
		
		//Then turn it into a PriorityQueue
		Iterator<MedicalBlock> it = incomingUBs.iterator();
		while (it.hasNext()) {
			System.out.println("...adding a block");
			this.blockHolder.ub.add(it.next());
		}
		
		System.out.println("blocks so far...-------------------------");
		this.blockHolder.printUb();
		System.out.println("---------------------------------");
		
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
	  public String toString() {
		  return BlockID + " " + VerificationProcessID + " " + PreviousHash + " " + Fname + " " + Lname;
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

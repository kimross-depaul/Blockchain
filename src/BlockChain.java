import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Time;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;

import javax.crypto.Cipher;

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
o Each server listens for the multi-casted UB's and adds them to its own PriorityQueue of UBs
- The UB queue needs to start with one dummy block with "Previous proof of work"
- Each server goes to work on the highest-priority block
o First solver stamps the solved UB (proved work) and multicasts just that VB to the other servers
o Each server updates its own local copy of the BlockChain (listening on 4930+processID)
- Server 0 writes it to the log file.
- EXCHANGING KEYS GAVE ME SOME TROUBLE.  TRY THIS from blockj.java
 	byte[] bytePubkey = keyPair.getPublic().getEncoded();
    System.out.println("Key in Byte[] form: " + bytePubkey);
    
    String stringKey = Base64.getEncoder().encodeToString(bytePubkey);
    System.out.println("Key in String form: " + stringKey);

Other details
o Process 2 startup triggers the running of the whole system.
- don't forget process 0 has to output the blockchain to BlockchainLedgerSample.json
- blocks are verified by SHA256 hash of
	1. previous hash
	2. random seed
	3. data (what is data, all the fields somehow?)
o the "ledger is marshaled to other nodes as json"
o credit is given to nodes for validating blocks


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

	public static void main(String[] args) throws InterruptedException, NoSuchAlgorithmException {	
		//Read data from text file
		String ProcessID = "0";
		if (args.length > 0) ProcessID = args[0];
		int intProcessID = Integer.valueOf(ProcessID);
		System.out.println("I'm process " + ProcessID);
		
		GsonBuilder gsonBuilder = new GsonBuilder();
		//gsonBuilder.registerTypeAdapter(PublicKey.class, new InterfaceAdapter());
		Gson gsonParser = new GsonBuilder().setPrettyPrinting().create();	
		
		//Create a ModeHolder and 2 servers:  the JokeServer (server) and the AdminServer
		BlockHolder blockHolder = new BlockHolder(NUM_CONSORTIUM_MEMBERS, KEY_PORT_PREFIX, UB_PORT_PREFIX, BLOCK_PORT_PREFIX, intProcessID);
		BlockServer keyServer = new BlockServer(MessageUtils.parseTargetPort(KEY_PORT_PREFIX, intProcessID), ProcessID, new KeyWorker(blockHolder), blockHolder);
		BlockServer UBserver = new BlockServer(MessageUtils.parseTargetPort(UB_PORT_PREFIX, intProcessID), ProcessID, new UnverifiedBlockWorker(blockHolder), blockHolder);
		BlockServer blockChainServer = new BlockServer(MessageUtils.parseTargetPort(BLOCK_PORT_PREFIX, intProcessID), ProcessID, new VerifiedBlockWorker(blockHolder), blockHolder);
		
		keyServer.start();
		UBserver.start();
		blockChainServer.start();
		
		do {
			Thread.sleep(5000);
			if (blockHolder.startProcessingVBs) {
				blockHolder.startCurrentWork();
			}			
		} while (!blockHolder.weAreDone);
		blockHolder.blockChain.print();
		MessageUtils.sendMessageToConsortium("DONE!", NUM_CONSORTIUM_MEMBERS, BLOCK_PORT_PREFIX);
		
	}
}
class MessageUtils {
	public static int parseTargetPort(String portPrefix, int processId) {
		String tempPort = portPrefix + processId;
		return Integer.valueOf(tempPort);
	}
	public static void sendMessageToConsortium(String msg, int numConsortiumMembers, String portPrefix) {
		for (int i=0; i < numConsortiumMembers; i++) {
			sendMessageToServer(msg, parseTargetPort(portPrefix, i));
		}
	}
	public static void sendMessageToServer(String msg, int port) {
		Socket socket;					//Generic connection between 2 hosts
	//	BufferedReader fromServer;		//We need to be able to read data from the server
		PrintStream toServer;			//We need to be able to send data to the server
	//	String textFromServer;
		
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
	/*public static void sendObjectToServer(Object obj, int port) {
		Socket socket = new Socket ("localhost", port);
		ObjectOutputStream objStream= new ObjectOutputStream(socket.getOutputStream());
		objStream.writeObject(obj);
		objStream.close();
	}
	public static Object getObjectFromServer(int port) {
		Socket socket = new Socket("localhost", port);
	//	BufferedReader fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		ObjectInputStream objStream = new ObjectInputStream(socket.getInputStream());
		return objStream.readObject();
	}*/
	public static void writeToLog(String msg, int intProcessID) {
		if (intProcessID > 0) return;
		File logFile = new File ("BlockchainLog.txt");
		try {
			logFile.createNewFile();
			FileWriter out;

			out = new FileWriter("BlockchainLog.txt");
			out.write(msg);
			out.close();
			
		} catch (IOException e) {
			System.out.println("Something bad happened while writing to the log file:  " + e.getMessage());
		}	
	}
	public static void writeLedger(MyPriorityQueue blockChain, int intProcessID) {
		if (intProcessID > 0) return;
		GsonBuilder gsonBuilder = new GsonBuilder();
		Gson gsonParser = new GsonBuilder().setPrettyPrinting().create();	

		File logFile = new File ("BlockchainLedger.json");
		try {
			logFile.createNewFile();
			FileWriter out;

			out = new FileWriter("BlockchainLedger.json");
			out.write(gsonParser.toJson(blockChain));
			out.close();
			
		} catch (IOException e) {
			System.out.println("Something bad happened while writing to the log file:  " + e.getMessage());
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
		KeyPair keyPair = generateKeyPair(0);// COME BACK seed);
		return keyPair.getPublic();
	}
	public static PrivateKey getMyPrivateKey(long seed) {
		KeyPair keyPair = generateKeyPair(0);// COME BACK seed);
		return keyPair.getPrivate();
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
	public PublicKey getPublicKey(int intProcessID, BlockHolder blockHolder) {
		return blockHolder.getKey(intProcessID);
	}
	public static void sendKeys(int intProcessID, int numConsortiumMembers, String keyPortPrefix) {
		//We send it encoded
		PublicKey temp = KeyUtils.getMyPublicKey(intProcessID);
	    byte[] bytePubkey = temp.getEncoded();
	    String stringKey = Base64.getEncoder().encodeToString(bytePubkey);
		
		for (int i = 0; i < numConsortiumMembers; i++) {
			//COME BACK TO THIS
			Gson gsonParser = new GsonBuilder().setPrettyPrinting().create();	
			MessageUtils.sendMessageToServer(intProcessID + stringKey, MessageUtils.parseTargetPort(keyPortPrefix, i));
		}		
	}
	public static PublicKey decodeKey(String stringKey) {
		//We get it as an encoded string and turn it back into a PublicKey
	    byte[] bytePubkey2  = Base64.getDecoder().decode(stringKey);
	    X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(bytePubkey2);
	    try {
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		    return keyFactory.generatePublic(pubSpec);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			e.printStackTrace();
		}
	    return null;
	}
	//Courtesy of Clark Elliot's BlockJ Sample Code
	public static byte[] signThis(byte[] dataToSign, PrivateKey privateKey) {
		Signature signer;
		try {
			signer = Signature.getInstance("SHA1withRSA");
			signer.initSign(privateKey);
			signer.update(dataToSign);
			return signer.sign();
		} catch (NoSuchAlgorithmException ae) {
			ae.printStackTrace();
		} catch (InvalidKeyException ke) {
			ke.printStackTrace();
		} catch (SignatureException se) {
			se.printStackTrace();
		}
		return null;
	}
	public static boolean verifySignature(byte[] dataThatsSigned, PublicKey publicKey, byte[] digitalSignature ) {
	    Signature signer;
		try {
			signer = Signature.getInstance("SHA1withRSA");
			signer.initVerify(publicKey);
			signer.update(dataThatsSigned);
			return (signer.verify(digitalSignature));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (SignatureException e) {
			e.printStackTrace();
		}
		return false;
	}
	public static String hashIt(String data) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
	   	md.update (data.getBytes());
	    byte byteData[] = md.digest();
		
	    StringBuffer sb = new StringBuffer();
	    for (int i = 0; i < byteData.length; i++) {
	      sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
	    }
    
	    return sb.toString();
	}
}
class MyPriorityQueue {
	private PriorityQueue<MedicalBlock> pq;
	
	public MyPriorityQueue() {
		pq = new PriorityQueue<MedicalBlock>();
	}
	public void addFakeBlock() throws NoSuchAlgorithmException {
		pq.add(new MedicalBlock());
	}
	public boolean add(MedicalBlock m) {
		Iterator<MedicalBlock> it = pq.iterator();
		while (it.hasNext()) {
			if (it.next().BlockID.contentEquals(m.BlockID)) {
				return false;
			}
		}
		pq.add(m);
		return true;
	}
	public MedicalBlock poll() {
		return pq.poll();
	}
	public MedicalBlock peek() {
		return pq.peek();
	}
	public int size() {
		return pq.size();
	}
	public void print() {
		Iterator<MedicalBlock> it = pq.iterator();
		System.out.println("-------------------------------------");
		while (it.hasNext()) {
			MedicalBlock b = it.next();
			System.out.println("==" + b.Fname + " " + b.Lname + " (" + b.VerificationProcessID + ")");
		}
		System.out.println("-------------------------------------");
	}
	public void remove(MedicalBlock m) {
		synchronized(this) {
			Iterator<MedicalBlock> it = pq.iterator();
			while (it.hasNext()) {
				MedicalBlock thisBlock = it.next();
				if (thisBlock.BlockID.contentEquals(m.BlockID)) {
					pq.remove(thisBlock);
					break;
				}
			}
		}
	}
	public String getTopHash() {
		MedicalBlock temp = pq.peek();
		System.out.println("(current prev block is " + temp.Fname + temp.Lname + " " + temp.WinningHash +  ")");
		return pq.peek().WinningHash;
	}
}
class BlockHolder {
	MyPriorityQueue ub;
	MyPriorityQueue blockChain;
	BlockWorker currentWork;
	ArrayList<PublicKey> consortiumKeys; //COME BACK
	// CONSIDER STORING ONLY THE KEY.GETMODULUS() AND KEY.GETpUBLICEXPONENT() RATHER THAN SERIALIZNG THE PUBLICKEY OBJECT, WHICH I CANT SEEM TO DO
	//ArrayList<String> consortiumKeys;
	int numConsortiumMembers;
	String KEY_PORT_PREFIX = "471";
	String UB_PORT_PREFIX = "482";
	String BLOCK_PORT_PREFIX = "493";
	int intProcessID;
	int keysReceived;
	int numMembersSentUBs;
	KeyPair myKeyPair;
	boolean wereUBsSent;
	boolean startProcessingKeys;
	boolean startProcessingUBs;
	boolean startProcessingVBs;
	boolean weAreDone; //COME BACK.  WE NEED TO SET THIS TO TRUE WHEN THE BLOCKCHAIN IS FULL.
	
	public BlockHolder(int _numConsortiumMembers, String _KEY_PORT_PREFIX, String _UB_PORT_PREFIX, String _BLOCK_PORT_PREFIX, int _intProcessID) throws NoSuchAlgorithmException {
		consortiumKeys = new ArrayList<PublicKey>(_numConsortiumMembers); //COME BACK
		//consortiumKeys = new ArrayList<String>(_numConsortiumMembers);
		numConsortiumMembers = _numConsortiumMembers;
		KEY_PORT_PREFIX = _KEY_PORT_PREFIX;
		UB_PORT_PREFIX = _UB_PORT_PREFIX;
		BLOCK_PORT_PREFIX = _BLOCK_PORT_PREFIX;
		intProcessID = _intProcessID;
		keysReceived = 0;
		numMembersSentUBs = 0;
		wereUBsSent = false;
		startProcessingKeys = false;
		startProcessingUBs = false;
		startProcessingVBs = false;
		weAreDone = false;
		ub = new MyPriorityQueue();
		blockChain = new MyPriorityQueue();
		System.out.print("Adding fake block...");
		blockChain.addFakeBlock();
		System.out.println("...added!");
		myKeyPair = KeyUtils.generateKeyPair(intProcessID);
		
		//The arraylist didn't like being populated out of order, so let's initialize each first
		for (int i = 0; i < numConsortiumMembers; i++) {
			consortiumKeys.add(i, null);
		}
		
	}
	public void addKey(String key, String processId, Gson gsonParser) {
		System.out.println("\tadding key to position " + processId);
		java.lang.reflect.Type publicKey = new TypeToken<PublicKey>() {}.getType();
		
		//COME BACK
		//consortiumKeys.set(Integer.valueOf(processId), key); //gsonParser.fromJson(key, publicKey));
		//PublicKey temp = gsonParser.fromJson(key,  publicKey);
		//consortiumKeys.set(Integer.valueOf(processId),  temp);
		consortiumKeys.set(Integer.valueOf(processId), KeyUtils.decodeKey(key));

		keysReceived ++;
	}
	public PublicKey getKey(int pos) {
//		return (PublicKey) consortiumKeys.get(pos);// COME BACK
		return KeyUtils.getMyPublicKey(1);
	}
	public boolean hasAllKeys() {
		if (keysReceived >= numConsortiumMembers) {
			System.out.println("We have all the keys.  Start processing Unverified Blocks.");
			startProcessingUBs = true;
			return true;
		}
		return false;
	}
	public void printKeys() {
		for (int i = 0; i < consortiumKeys.size(); i++) {
			System.out.println(i + ":  " + consortiumKeys.get(i));
		}
	}
	public void addNewUBs(ArrayList<MedicalBlock> incomingUBs) {
		Iterator<MedicalBlock> it = incomingUBs.iterator();
		boolean hadOne = false;
		while (it.hasNext()) {
			ub.add(it.next());
			hadOne = true;
		}
		if (hadOne) numMembersSentUBs ++;
		
		if (numMembersSentUBs >= numConsortiumMembers) {
//		if (ub.size() >= 11) {
			startProcessingVBs = true;
			System.out.println("I have this many UBs from " + numMembersSentUBs + " members:  " + ub.size() + ".  top block is " + ub.peek().toString());
		}
	}
	public void stopCurrentWork() {
		if (currentWork != null) {
			currentWork.interrupt();
			currentWork = null;
		}
	}
	public void startCurrentWork() {
		if (currentWork == null && startProcessingVBs) {
			TimeStamper.printNow("\tStarting Current Work (" + currentWork + "," + startProcessingVBs + ")");
			MedicalBlock topUnverifiedBlock = ub.poll(); 
			if (topUnverifiedBlock == null) {
				weAreDone = true;
				startProcessingVBs = false;
			}else {
				currentWork = new BlockWorker(topUnverifiedBlock, this);
				currentWork.start(); 
			}
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
		
		//Perform setup (whatever that means for your server's purpose)
		worker.initialize();
		
		//Listen for communications from the consortium
		while (worker.keepRunning()) { 
			try {
				System.out.println("Server Running on port " + port);
			
				socket = serverSocket.accept();
				worker.setSocket(socket);
				worker.run();
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}	
		}
		//You must be done with your work - close up the socket.		
		try {
			System.out.println("Closing the server on port " + port + " - its work is done.");
			socket.close();
		} catch (NullPointerException nullex) {
			System.out.println("Couldn't close the socket, it wasn't initialized yet.");
		} catch (IOException e) {
			e.printStackTrace();
		}
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
	public void initialize();
	public Boolean keepRunning();
	
}
class KeyWorker extends Thread implements IWorker {
	Socket socket;
	BlockHolder blockHolder;
	Boolean wasMyKeySent;
	
	KeyWorker (BlockHolder _blockHolder) {
		this.blockHolder = _blockHolder;
		wasMyKeySent = false;
	}
	@Override
	public void setSocket(Socket _socket) {
		this.socket = _socket;
	}
	public void initialize() {
		if (blockHolder.intProcessID == 2) {
			//I need to tell the servers to start exchanging keys!
			for (int i = 0; i < blockHolder.numConsortiumMembers; i++) {
				MessageUtils.sendMessageToServer("START!", MessageUtils.parseTargetPort(blockHolder.KEY_PORT_PREFIX, i));
			}
		}
	}
	public Boolean keepRunning() {
		return !blockHolder.hasAllKeys();
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
					sb.append(result);
				}					
			
				if (blockHolder.startProcessingKeys == false && sb.toString().contentEquals("START!")) {
					blockHolder.startProcessingKeys = true;
					KeyUtils.sendKeys(blockHolder.intProcessID, blockHolder.numConsortiumMembers, blockHolder.KEY_PORT_PREFIX);
					wasMyKeySent = true;
				}else {
					String key = "";
					String processId = "";
					
					whatFailed = sb.toString(); //if this fails, I want to see what it was.
					//They'll send us 1 digit for their process ID and the rest is their key.
					if (sb.toString().length() > 0) {
						processId = sb.toString().substring(0,1);
						key = sb.toString().substring(1, sb.toString().length());
					}
					
					//ADDING THE KEY TO OUR KEY-ARRAY
					Gson gsonParser = new GsonBuilder().setPrettyPrinting().create();
//					blockHolder.addKey(gsonParser.toJson(key), processId, gsonParser);
					blockHolder.addKey(key,  processId, gsonParser);
				}
			}catch (NumberFormatException numex) {
				System.out.println("Server received an invalid key from a consortium member: {" + whatFailed + "} on port " + this.socket.getLocalPort());
			}catch (IOException iox) {
				//If we're here, we couldn't read incoming data from the client
				System.out.println("Key Server encountered a read-error: " + iox.getMessage());
			}
			socket.close();
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
	public void initialize()  {
		if (!blockHolder.wereUBsSent) {
			//we wait until the keys are processed before we start on our blocks.
			//i wonder if all 3 servers should communicate with each other "do you have all the keys"
			//or "do you have my key?, I can resend it"
			//and then no one starts sending UBs until all keys are exchanged
			while (blockHolder.startProcessingUBs == false) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			//2.  When we have all the keys, send/receive UBs
			Gson gsonParser = new GsonBuilder().setPrettyPrinting().create();
			sendMyUBs(blockHolder.intProcessID, blockHolder, gsonParser);
		}
	}
	public Boolean keepRunning() {
		//if we're processing blocks, then we have all the unverified ones.
		return !blockHolder.startProcessingVBs;
	}
	private void sendMyUBs(int intProcessID, BlockHolder blockHolder, Gson gsonParser) {
		PriorityQueue<MedicalBlock> fileUBs = readBlocksFromFile("BlockInput" + intProcessID + ".txt");
		
		for (int i = 0; i < blockHolder.numConsortiumMembers; i++) {	
			MessageUtils.sendMessageToServer(gsonParser.toJson(fileUBs), MessageUtils.parseTargetPort(blockHolder.UB_PORT_PREFIX, i));
		}		
		blockHolder.wereUBsSent = true;
	}
	private PriorityQueue<MedicalBlock> readBlocksFromFile(String fileName) {
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
			ub.add(new MedicalBlock(ret.get(i), blockHolder.intProcessID + ""));
		}
		return ub;
	}
	public void run() {
		System.out.println("Unverified Block Worker is running...");
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
				
				//READING UNVERIFIED BLOCKS FROM THE CONSORTIUM
				System.out.println("-----------------" + in.ready() + "--------------------------");
				while ((result = in.readLine()) != null) {
					sb.append(result);
				}					
				
				//ADDING UNVERIFIED BLOCKS TO THE UB CHAIN
				updateUnverifiedBlocks(sb.toString());
				
				this.blockHolder.ub.print();
				
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
	private void updateUnverifiedBlocks(String json) {
		//GSON needs to know what type of object to return
		java.lang.reflect.Type ubArray = new TypeToken<ArrayList<MedicalBlock>>() {}.getType();
		
		//Convert the json string into an ArrayList
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		ArrayList<MedicalBlock> incomingUBs = gson.fromJson(json, ubArray);
		if (incomingUBs != null) blockHolder.addNewUBs(incomingUBs);	
	}
}

class VerifiedBlockWorker extends Thread implements IWorker {
	Socket socket;
    BlockHolder blockHolder;
	
	VerifiedBlockWorker (BlockHolder _blockHolder) { //ModeHolder _modeHolder) {
		this.blockHolder = _blockHolder;
	}
	public void initialize()  {
		while (blockHolder.startProcessingVBs == false) {
			try {
				System.out.println("... not ready for puzzles...");
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		MessageUtils.sendMessageToServer("START!", MessageUtils.parseTargetPort(blockHolder.BLOCK_PORT_PREFIX, blockHolder.intProcessID));
	}
	public Boolean keepRunning() {
		return !blockHolder.weAreDone;
	}
	@Override
	public void setSocket(Socket _socket) {
		this.socket = _socket;
	}
	public void run() {
/*		System.out.println("88888888888888888888888888888888888888888888888888888888888888888");
		System.out.println("88888888888888888888888888888888888888888888888888888888888888888");
		System.out.println("BLOCK WORKER.RUN");
		System.out.println("88888888888888888888888888888888888888888888888888888888888888888");
		System.out.println("88888888888888888888888888888888888888888888888888888888888888888");*/
		/*
		 * the vb server start running - it's listening for verified blocks
		 * another thread (thread2) needs to do work on a block.
		 * If we receive the solution for the current block, thread2, gets interrupted by VerifiedBlockworker
		 * 		Is this the block you're working on?  don't bother.
		 * then thread2 starts again on the next UB
		 * if we're the winner, we add the verified block and blast it to the other consortium members.
		 */
		PrintStream out = null;
		BufferedReader in = null;
		try {			
			in = new BufferedReader (new InputStreamReader(socket.getInputStream()));
			out = new PrintStream(socket.getOutputStream());
			
			try {
				//EVERY TIME WE RECEIVE SOMETHING WE START WORKING ON ANOTHER BLOCK
				//INSTEAD WE NEED THEM LAUNCHED SEPARATELY.  
				//A 4TH THREAD FROM MAIN?  
				//2 BLOCK CHAIN THREADS. 1 TO LISTEN FOR VERIFIED BLOCKS AND 1 TO WORK ON BLOCKS
				//WHEN THREAD 2 GETS A NEW BLOCK, IT INTERRUPTS THREAD 1.
				/*BlockWorker blockWorker = null;
				MedicalBlock topUnverifiedBlock = null;
				
				if (blockHolder.startProcessingVBs) {
					topUnverifiedBlock = blockHolder.ub.poll(); 
					blockWorker = new BlockWorker(topUnverifiedBlock, blockHolder);
					blockWorker.start();
				}
				*/
				
				//Get the blocks the consortium has solved
				StringBuilder sb = new StringBuilder();
				String result = "";
				
				//READING VERIFIED BLOCKS FROM THE CONSORTIUM
				while ((result = in.readLine()) != null) {
					sb.append(result);
				}					
				
				if (sb.toString().contentEquals("START!")) {
					//Other forms of communication:  signal to start or end
					//We accept these without processing a block (there isn't one in this message)
					blockHolder.startProcessingVBs = true;
				}else if (sb.toString().contentEquals("DONE!")) {
					blockHolder.weAreDone = true;
				}else {					
					//ADDING BLOCKS VERIFIED BY SOMEONE ELSE TO THE BLOCKCHAIN (with some verification...)
					String senderProcID = sb.toString().substring(0, 1);
					String jsonBlock = sb.toString().substring(1, sb.toString().length());
					String blockID = updateVerifiedBlocks(jsonBlock);

					//IF WE'RE STILL WORKING ON THAT BLOCK, INTERRUPT US.
					//System.out.println("Does " + blockID.substring(0, 4) + " = " + topUnverifiedBlock.BlockID.substring(0, 4) + "???");
					/*if (blockID.contentEquals(topUnverifiedBlock.BlockID)) {
						System.out.println("STOP WORKING ON THIS BLOCK - YOU LOST!");
						blockWorker.interrupt();
					}
					*/
									
					//this.blockHolder.blockChain.print();
				}

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
	private String updateVerifiedBlocks(String json)  {
		//GSON needs to know what type of object to return
		java.lang.reflect.Type ub = new TypeToken<MedicalBlock>() {}.getType();
		
		//Convert the json string into a MedicalBlock
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		MedicalBlock newBlock = gson.fromJson(json, ub);	
		TimeStamper.printNow("We received a solved block for " + newBlock.Fname + " " + newBlock.Lname + " (" + newBlock.VerificationProcessID + ")");
		
		//validate it yourself... make sure it's good
		boolean isValid = false;
		try {
			isValid = newBlock.validate();
		} catch (NoSuchAlgorithmException e) {
			System.out.println("Encryption not supported:  " + e.getMessage());
		}
				
		//Add to the chain
		if (isValid) {
			blockHolder.stopCurrentWork();
			blockHolder.blockChain.add(newBlock);
			blockHolder.ub.remove(newBlock); //make sure we don't process this block if we're late getting to it		
			System.out.println("<<<<< VALID BLOCK! >>>>>");
		}else {
			System.out.println("<<<<<<<<<<< THIS BLOCK DOESN'T PASS VALIDATION!!!>>>>>>>>>>");
		}
				
		//this.blockHolder.blockChain.print();
		if (isValid) {
			return newBlock.BlockID;
		}else {
			return "";
		}	
	}
}
class BlockWorker extends Thread {
	MedicalBlock currentBlock;
	BlockHolder blockHolder;
	
	public BlockWorker(MedicalBlock _ub, BlockHolder _blockHolder) {
		this.currentBlock = _ub;
		blockHolder = _blockHolder;
	}
	public void run() {
		try {
			System.out.println("\n8888888888888888888888888888888888888888888888888888888888888888");
			TimeStamper.printNow("Working on " + currentBlock.Fname + " " + currentBlock.Lname );
			//do work on the ub
			int randval = 27; // Just some unimportant initial value
			int tenths = 0;
			Random r = new Random();
			for (int i=0; i<1000; i++){ // safety upper limit of 1000
				Thread.sleep(100); // not really work because can be defeated, but OK for our purposes.
				randval = r.nextInt(100); // Higher val = more work
				System.out.print(".");
				if (randval < 4) {       // Lower threshold = more work
					tenths = i;
					break;
				}
			}
			System.out.println(" <-- We did " + tenths + " tenths of a second of *work*.\n");

			//Set the winning answer for our fake work
			currentBlock.RandomSeed = randval + "";
			
			//Give credit to the winner
			currentBlock.VerificationProcessID = blockHolder.intProcessID + "";
			
			//Set the Winning Hash from the previous solved block
			currentBlock.PreviousHash = blockHolder.blockChain.getTopHash();

			//Hash it together to make the new WinningHash
			String toHash = currentBlock.PreviousHash + randval + currentBlock.toString();
			String encrypted = KeyUtils.hashIt(toHash);
			currentBlock.WinningHash = encrypted;			
			
				//			Thread.sleep(3000);

			Gson gsonParser = new GsonBuilder().setPrettyPrinting().create();
			System.out.println("I SOLVED " + currentBlock.Fname + " " + currentBlock.Lname + "!");
			//COME BACK:  We need to hash things in this block and its predecesor before sending it to the consortium
			MessageUtils.sendMessageToConsortium(blockHolder.intProcessID + gsonParser.toJson(currentBlock), blockHolder.numConsortiumMembers, blockHolder.BLOCK_PORT_PREFIX);
		}catch (InterruptedException ie) {
			System.out.println("***************************** Interrupted! Move on to another block ************************************");
		}catch (NoSuchAlgorithmException ex) {
			System.out.println("Encryption method not supported:  " + ex.getMessage());
		}
	}
}
class MedicalBlock implements Comparable {
	//I need to sign the blockID with the creator's key
	//The 3 things to combine for verifying a block are:
	/*
	 * 	Previous Hash
	 *  random seed
	 *  data
	 * 
	 * Start by producing S
	 * 		Concatenate SHA-256 hash of previous block
	 * 		Data in the current block
	 * 		A random string R
	 * 
	 * Then Hash S, producing S-HASH
	 * 
	 * 
	 * 
	 */
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
	  String RandomSeed; // Our guess. Ultimately our winning guess.
	  String WinningHash;
	  String timeStamp;
	  byte[] signedByCreator;
	 
	  
	//John Smith 1996.03.07 123-45-6789 Chickenpox BedRest aspirin

	  public MedicalBlock(String fileInput, String procID) {
		  String[] vals = fileInput.split(" ");
		  BlockID = UUID.randomUUID().toString();
		  signedByCreator = KeyUtils.signThis(BlockID.getBytes(), KeyUtils.getMyPrivateKey(Long.valueOf(procID)));
		  Fname = vals[0];
		  Lname = vals[1];
		  DOB = vals[2];
		  SSNum = vals[3];
		  Diag = vals[4];
		  Treat = vals[5];
		  Rx = vals[6];
		  
		  //Courtesy of Clark Elliot's BlockJ example
		  Date date = new Date();
		  String T1 = String.format("%1$s %2$tF.%2$tT", "", date); // Create the TimeStamp string.	  
		  timeStamp = T1 + "." + procID; 
		  VerificationProcessID = "";
	  }
	public MedicalBlock() throws NoSuchAlgorithmException { //For initial fake block;
		BlockID = UUID.randomUUID().toString();
		signedByCreator = KeyUtils.signThis(BlockID.getBytes(), KeyUtils.getMyPrivateKey(Long.valueOf(0)));
		VerificationProcessID = "0";
		PreviousHash = "1";
		Fname = "Kim";
		Lname = "Ross";
		DOB = "11/11/1970";
		SSNum="111-111-1111";
		Diag = "Healthy";
		Treat = "Ice Cream";
		Rx = "Coffee";
		Date date = new Date();
		String T1 = String.format("%1$s %2$tF.%2$tT", "", date); // Create the TimeStamp string.	  
		timeStamp = T1 + "." + 0; 
		RandomSeed = "0";
		String toHash = "1" + "0" + this.toString();
		String encrypted = KeyUtils.hashIt(toHash);
		WinningHash = encrypted;

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
		return this.timeStamp.compareTo(m.timeStamp);
		
		/*if (m.timeStamp.before(this.timeStamp)) {
			return -1;
		}else if (m.timeStamp.after(this.timeStamp)) {
			return 1;
		}else {
			return 0; //must be the same?
		}*/
		
	}
	public boolean validate() throws NoSuchAlgorithmException {
		  //COME BACK
		  //NEED TO CHECK THAT THE WINNING HASH WORKS PROPERLY
//			prev hash + seed + data will equal the re-hash of that
		System.out.println("VALIDATING:  " + this.toString());
		String toCheck = PreviousHash + RandomSeed + this.toString();
		String encrypted = KeyUtils.hashIt(toCheck);
		
		return encrypted.contentEquals( WinningHash);
	}
	@Override
	public String toString() {
		String s = "|";
		return "F=" +Fname + ",L=" +Lname + ",B=" + BlockID + ",D="+ DOB + ",S=" + SSNum + ",DTR=" + Diag + Treat + Rx + ",V=" +VerificationProcessID + ",PH=" + PreviousHash;  
	}
}	

class TimeStamper {
	static void printNow(String msg) {
		java.text.SimpleDateFormat formatter= new java.text.SimpleDateFormat("HH:mm:ss z");
		java.util.Date date = new java.util.Date(System.currentTimeMillis());
		System.out.println(msg + " at " + formatter.format(date));		
	}
}
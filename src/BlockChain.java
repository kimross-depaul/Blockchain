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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;
import java.io.*;

/* ***************************************************************************
***************************** REQUESTED HEADER *****************************
****************************************************************************
*
*	1.  NAME:  			Kim Ross 	DATE:  11/3/2020
*	2.  JAVA VERSION:  	Java SE-9 (from JDK 11.0.5)
*	3.  RUN INSTRUCTIONS:
*
*		This application has 3 servers:
*			- One to process exchanges of PublicKeys with other running instances
*			- One to exchange unverified block-chain data from text files
*			- One to process verified block-chain items
*
*		It was designed to be run in a particular way:
*			- 3 "consortium" members (instances of this application) must run in parallel
*			- The instances must be started in order (process 0, 1, then 2)
*			- Once process 2 is running, it communicates with the other 2 to begin.
*
*		Servers communicate on the following ports:
*			Instance #		Key Server		Unverified Server		Verified Server
*__________________________________________________________________________________________
*			0				4710			4820					4930
*			1				4711			4821					4931
*			2				4712			4822					4932
*		
*		To Compile:  
*		javac -cp "gson-2.8.2.jar" BlockChain.java
*
*		To Run:
*		In 3 command windows or via some other means, this program needs to run 3 times:
*		java -cp ".;gson-2.8.2.jar" BlockChain 0
*		java -cp ".;gson-2.8.2.jar" BlockChain 1
*		java -cp ".;gson-2.8.2.jar" BlockChain 2
*		
*	4.  EXAMPLES 
*		Below are samples of the text this program was designed to read:
*
*		John Smith 1996.03.07 123-45-6789 Chickenpox BedRest aspirin
*		Joe  Blow  1996.03.07 123-45-6888 Smallpox BedRest Whiskey
*		Julie Wilson 1996.03.07 123-45-6999 Insomnia Exercise HotPeppers
*		Wayne Blaine 1942.07.07 123-45-6777 Measles WaitToGetBetter CodLiverOil 
*
*		The values must appear in that order and must be separated by spaces.
*
* 	5.  NOTES:
* 		
* 		At a high level, this program does the following:
* 		- Reads input files with medical information
* 		- Marshals this data to 3 instances of this program (including itself) 
* 		- The 3 instances of this program compete to "solve" the block
* 		- Winners multi-cast the block they solved
* 		- Receivers verify it and add it to their own ledger
*
*		Outputs:
*		- BlockchainLog.txt	contains the console output from instance #0
*		- BlockchainLedger.json contains the solved blocks (instance #0 creates this file)
*
* 	Any references to "blockj sample code" in the code comments are credited to the following sources:
* 
* https://mkyong.com/java/how-to-parse-json-with-gson/
* http://www.java2s.com/Code/Java/Security/SignatureSignAndVerify.htm
* https://www.mkyong.com/java/java-digital-signatures-example/ (not so clear)
* https://javadigest.wordpress.com/2012/08/26/rsa-encryption-example/
* https://www.programcreek.com/java-api-examples/index.php?api=java.security.SecureRandom
* https://www.mkyong.com/java/java-sha-hashing-example/
* https://stackoverflow.com/questions/19818550/java-retrieve-the-actual-value-of-the-public-key-from-the-keypair-object
* https://www.java67.com/2014/10/how-to-pad-numbers-with-leading-zeroes-in-Java-example.html
*
*	The base code for the "...Server" and "...Worker" classes were adapted from Clark Elliott's InetServer

*/
//THE BLOCKCHAIN CLASS CONTAINS ONLY A MAIN METHOD
//IT DEFINES THE PORT NUMBERS AND STARTS THE SERVERS AND BLOCK-WORKER THREAD
public class BlockChain extends Thread {
	public static int NUM_CONSORTIUM_MEMBERS = 3;
	public static String KEY_PORT_PREFIX = "471";
	public static String UB_PORT_PREFIX = "482";
	public static String BLOCK_PORT_PREFIX = "493";

	public static void main(String[] args) throws InterruptedException, NoSuchAlgorithmException {	
		//GET OUR PROCESS NUMBER - 0, 1, OR 2
		String ProcessID = "0";
		if (args.length > 0) ProcessID = args[0];
		int intProcessID = Integer.valueOf(ProcessID);
		MessageUtils.writeToLog("I'm process " + ProcessID, intProcessID);
			
		//THE BLOCKHOLDER STORES SHARED VALUES BETWEEN THE 3 SERVERS
		BlockHolder blockHolder = new BlockHolder(NUM_CONSORTIUM_MEMBERS, KEY_PORT_PREFIX, UB_PORT_PREFIX, BLOCK_PORT_PREFIX, intProcessID);

		//THE 3 SERVERS EACH HAVE THEIR OWN WORKER (FOR KEYS, UNVERIFIED OR VERIFIED BLOCK WORK)
		BlockServer keyServer = new BlockServer(MessageUtils.parseTargetPort(KEY_PORT_PREFIX, intProcessID), ProcessID, new KeyWorker(blockHolder), blockHolder);
		BlockServer UBserver = new BlockServer(MessageUtils.parseTargetPort(UB_PORT_PREFIX, intProcessID), ProcessID, new UnverifiedBlockWorker(blockHolder), blockHolder);
		BlockServer blockChainServer = new BlockServer(MessageUtils.parseTargetPort(BLOCK_PORT_PREFIX, intProcessID), ProcessID, new VerifiedBlockWorker(blockHolder), blockHolder);
		
		//START THE SERVERS
		keyServer.start();
		UBserver.start();
		blockChainServer.start();
		
		//THIS 4TH THREAD WORKS ON BLOCK PUZZLES.
		//IT WILL START WORK WHEN THE UNVERIFIED BLOCK QUEUE IS FULL.
		do {
			Thread.sleep(5000);
			if (blockHolder.startProcessingVBs) {
				blockHolder.startCurrentWork();
			}			
		} while (!blockHolder.weAreDone);
		
		//WRITE THE FINISHED BLOCKCHAIN TO A TEXT FILE
		MessageUtils.writeLedger(blockHolder.blockChain, intProcessID);
		
		//TELL THE CONSORTIUM WE'RE DONE - THEY CAN SHUT DOWN NOW.
		MessageUtils.sendMessageToConsortium("DONE!", NUM_CONSORTIUM_MEMBERS, BLOCK_PORT_PREFIX, intProcessID);
		
	}
}

//UTILITIES FOR SENDING MESSAGES TO OTHER SERVERS AND LOGGING
class MessageUtils {
	//CONVENIENCE METHOD THAT TAKES A PORT-PREFIX AND YOUR PROCESSID TO MAKE A PORT NUMBER
	public static int parseTargetPort(String portPrefix, int processId) {
		String tempPort = portPrefix + processId;
		return Integer.valueOf(tempPort);
	}
	//SENDS THE SAME MESSAGE TO ALL MEMBERS OF THE CONSORTIUM
	public static void sendMessageToConsortium(String msg, int numConsortiumMembers, String portPrefix, int intProcessID) {
		for (int i=0; i < numConsortiumMembers; i++) {
			sendMessageToServer(msg, parseTargetPort(portPrefix, i), intProcessID);
		}
	}
	//SENDS A MESSAGE TO ONE SERVER
	public static void sendMessageToServer(String msg, int port, int intProcessID) {
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
		
			//Close the socket, we're done.
			socket.close();
			
		}catch (IOException iox) {
			//Reading and Writing can throw an IOException, which we have to handle
			if (msg.contentEquals("DONE!")) {
				MessageUtils.writeToLog("Consortium member at port " + port + " is shut down.", intProcessID);
			}else {
				MessageUtils.writeToLog("Error reading from server: " + iox.getMessage(), intProcessID);
			}
		}
	}
	//WRITES TO THE BLOCKCHAINLOG.TXT FILE
	public static void writeToLog(String msg, int intProcessID, String suffix) {
		System.out.print(msg + suffix);
		if (intProcessID > 0) return;
		File logFile = new File ("BlockchainLog.txt");
		try {
			logFile.createNewFile();
			FileWriter out;

			out = new FileWriter("BlockchainLog.txt", true);
			out.write(msg + suffix);
			out.close();			
		} catch (IOException e) {
			System.out.println("Something bad happened while writing to the log file:  " + e.getMessage());
		}		
	}
	//OVERLOADED - WHEN YOU DON'T WANT THE NEWLINE APPENDED
	public static void writeToLog(String msg, int intProcessID) {
		MessageUtils.writeToLog(msg,  intProcessID, "\n");
	}
	//WRITES THE FINISHED BLOCKCHAIN TO BLOCKCHAINLEDGER.JSON
	public static void writeLedger(MyPriorityQueue blockChain, int intProcessID) {
		//print to the console as well
		blockChain.print(intProcessID, "\nFULL BLOCKCHAIN WITH CREDIT:\n");
		
		//Only process 0 writes to the ledger file.
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
			MessageUtils.writeToLog("Something bad happened while writing to the log file:  " + e.getMessage(), intProcessID);
		}		
	}
}
//UTILITIES FOR GETTING KEYS, SIGNING, AND VALIDATING SIGNATURES
class KeyUtils {
	//THE KEYS ARE STORED IN THE BLOCKHOLDER CLASS BUT RETRIEVED HERE
	public static PublicKey getMyPublicKey(BlockHolder blockHolder) {
		return blockHolder.myKeyPair.getPublic();
	}
	public static PrivateKey getMyPrivateKey(BlockHolder blockHolder) {
		return blockHolder.myKeyPair.getPrivate();
	}
	public PublicKey getPublicKey(int intProcessID, BlockHolder blockHolder) {
		return blockHolder.getKey(intProcessID);
	}
	//GENERATES MY KEY PAIR.  CODE COURTESY OF CLARK ELLIOT'S BLOCKJ.JAVA
	public static KeyPair generateKeyPair(long seed)  {
	    KeyPairGenerator keyGenerator = null;
		try {
			keyGenerator = KeyPairGenerator.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			MessageUtils.writeToLog(e.getMessage(), (int)seed);
		}
	    SecureRandom rng = null;
		try {
			rng = SecureRandom.getInstance("SHA1PRNG", "SUN");
		} catch (NoSuchAlgorithmException | NoSuchProviderException e) {
			MessageUtils.writeToLog(e.getMessage(), (int)seed);
		}
	    rng.setSeed(seed);
	    keyGenerator.initialize(1024, rng);
	    
	    return (keyGenerator.generateKeyPair());
	}
	//SENDS MY KEY TO THE CONSORTIUM.  
	public static void sendKeys(BlockHolder blockHolder) {
		int intProcessID = blockHolder.intProcessID;
		int numConsortiumMembers = blockHolder.numConsortiumMembers;
		String keyPortPrefix = blockHolder.KEY_PORT_PREFIX;
		
		//We send it encoded
		PublicKey temp = KeyUtils.getMyPublicKey(blockHolder);
	    byte[] bytePubkey = temp.getEncoded();
	    String stringKey = Base64.getEncoder().encodeToString(bytePubkey);
		
		for (int i = 0; i < numConsortiumMembers; i++) {
			//WE CONCATENATE OUR PROCESS ID AND OUR ENCODED KEY WHEN SENDING TO THE CONSORTIUM
			MessageUtils.sendMessageToServer(intProcessID + stringKey, MessageUtils.parseTargetPort(keyPortPrefix, i), blockHolder.intProcessID);
		}		
	}
	//ENCODING/DECODING COURTESY OF CLARK ELLIOT'S BLOCKJ SAMPLE
	public static PublicKey decodeKey(String stringKey) {
		//To send to the consortium, it must be encoded when sent.
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
	//FOR SIGNING SOME DATA.  COURTESY OF CLARK ELLIOTT'S BLOCKJ SAMPLE CODE
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
	//TO VERIFY A SIGNATURE.   COURTESY OF CLARK ELLIOTT'S BLOCKJ SAMPLE CODE
	public static boolean verifySignature(byte[] dataThatsSigned, PublicKey publicKey, byte[] digitalSignature ) {
		//WE GET AN INSTANCE OF THE SHA1 ALGORITHM,
		//APPLY IT TO THE ORIGINAL, THEN MAKE SURE BOTH OUTPUTS MATCH
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
	//FOR HASHING DATA.  COURTESY OF CLARK ELLIOTT'S BLOCKJ SAMPLE CODE
	public static String hashIt(String data) throws NoSuchAlgorithmException {
		//RETURNS A BYTE ARRAY OF YOUR DATA HASHED WITH SHA-256
		MessageDigest md = MessageDigest.getInstance("SHA-256");
	   	md.update (data.getBytes());
	    byte byteData[] = md.digest();
		
	    //CONVERTS IT TO A STRING
	    StringBuffer sb = new StringBuffer();
	    for (int i = 0; i < byteData.length; i++) {
	      sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
	    }
    
	    return sb.toString();
	}
}
//WRAPPER FOR A PRIORITYQUEUE WITH MY OWN CONVENIENCE METHODS
class MyPriorityQueue {
	private PriorityQueue<MedicalBlock> pq;
	
	public MyPriorityQueue() {
		pq = new PriorityQueue<MedicalBlock>();
	}
	//MAKE SURE WE DON'T ADD A DUPLICATE
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
	//FOR PRINTING OUT A CHAIN OF VERIFIED OR UNVERIFIED BLOCKS
	public void print(int intProcessID, String prefixMessage) {
		Iterator<MedicalBlock> it = pq.iterator();
		MessageUtils.writeToLog(prefixMessage, intProcessID);
		MessageUtils.writeToLog("-------------------------------------", intProcessID);
		while (it.hasNext()) {
			MedicalBlock b = it.next();
			MessageUtils.writeToLog("-" + b.Fname + " " + b.Lname + " (" + b.VerificationProcessID + ")", intProcessID);
		}
		MessageUtils.writeToLog("-------------------------------------", intProcessID);
	}
	//WHEN SOMEONE BEAT US IN SOLVING A PUZZLE, WE NEED TO REMOVE THIS FROM UNVERIFIED BLOCKS QUEUE
	public void remove(MedicalBlock m) {
		//FOR THREAD-SAFETY
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
	//CONVENIENCE METHOD FOR GETTING THE PREVIOUS HASH WHEN WE SOLVE A PUZZLE
	public String getTopHash(int intProcessID) {
		MedicalBlock temp = pq.peek();
		MessageUtils.writeToLog("(current prev block is " + temp.Fname + " " + temp.Lname + ")", intProcessID);// + " " + temp.WinningHash +  ")");
		return pq.peek().WinningHash;
	}
}
//CLASS TO HOLD USEFUL VARIABLES AND SHARED RESOURCES BETWEEN THE SERVERS
class BlockHolder {
	//THE UNVERIFIED AND VERIFIED BLOCK CHAINS
	MyPriorityQueue ub;
	MyPriorityQueue blockChain;
	
	//THE CURRENT WORK BEING DONE
	BlockWorker currentWork;
	
	//CONSORTIUM AND SERVER VARIABLES
	ArrayList<PublicKey> consortiumKeys; 
	int numConsortiumMembers;
	String KEY_PORT_PREFIX = "471";
	String UB_PORT_PREFIX = "482";
	String BLOCK_PORT_PREFIX = "493";
	
	//MY PROCESS ID
	int intProcessID;
	
	//FOR KEY PROCESSING
	int keysReceived;
	KeyPair myKeyPair;

	//FLAGS SO SERVERS KNOW WHAT STAGE WE'RE IN
	int numMembersSentUBs;
	boolean wereUBsSent;
	boolean startProcessingKeys;
	boolean startProcessingUBs;
	boolean startProcessingVBs;
	boolean weAreDone; 
	
	public BlockHolder(int _numConsortiumMembers, String _KEY_PORT_PREFIX, String _UB_PORT_PREFIX, String _BLOCK_PORT_PREFIX, int _intProcessID) throws NoSuchAlgorithmException {
		consortiumKeys = new ArrayList<PublicKey>(_numConsortiumMembers); 
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
		myKeyPair = KeyUtils.generateKeyPair(intProcessID);
		blockChain.add(new MedicalBlock(KeyUtils.getMyPrivateKey(this)));
		
		//The arraylist didn't like being populated out of order, so let's initialize each first
		for (int i = 0; i < numConsortiumMembers; i++) {
			consortiumKeys.add(i, null);
		}
		
	}
	//ADDS A KEY RECEIVED FROM THE CONSORTIUM
	public void addKey(String key, String processId, Gson gsonParser, int intProcessID) {
		MessageUtils.writeToLog("\tAdding key to position " + processId, intProcessID);
		consortiumKeys.set(Integer.valueOf(processId), KeyUtils.decodeKey(key));

		keysReceived ++;
	}
	//WHEN WE NEED TO CHECK A SIGNATURE, WE'LL NEED A CONSORTIUM MEMBER'S PUBLIC KEY
	public PublicKey getKey(int pos) {
		return (PublicKey) consortiumKeys.get(pos);
	}
	//IF WE HAVE ALL THE KEYS, WE MOVE ONTO PROCESSING UNVERIFIED BLOCKS.
	public boolean hasAllKeys() {
		if (keysReceived >= numConsortiumMembers) {
			MessageUtils.writeToLog("We have all the keys.  Start processing Unverified Blocks.", intProcessID);
			startProcessingUBs = true;
			return true;
		}
		return false;
	}
	//CONVENIENCE METHOD TO SEE OUR KEYS
	public void printKeys() {
		for (int i = 0; i < consortiumKeys.size(); i++) {
			MessageUtils.writeToLog(i + ":  " + consortiumKeys.get(i), intProcessID);
		}
	}
	//WE READ AN ARRAYLIST FROM FILES - THIS ADDS THOSE BLOCKS TO THE UNVERIFIED BLOCK CHAIN
	public void addNewUBs(ArrayList<MedicalBlock> incomingUBs) {
		Iterator<MedicalBlock> it = incomingUBs.iterator();
		boolean hadOne = false;
		while (it.hasNext()) {
			ub.add(it.next());
			hadOne = true;
		}
		if (hadOne) numMembersSentUBs ++;
		
		if (numMembersSentUBs >= numConsortiumMembers) {
			startProcessingVBs = true;
			MessageUtils.writeToLog("I have this many Unverified Blocks from " + numMembersSentUBs + " members:  " + ub.size() + ".  Top block is " + ub.peek().toTitle(), intProcessID);
		}
	}
	//WHEN SOMEONE BEAT US, WE NEED TO INTERRUPT THE WORKER
	public void stopCurrentWork() {
		if (currentWork != null) {
			currentWork.interrupt();
			currentWork = null;
		}
	}
	//WHEN WE'RE READY TO PROCESS UNVERIFIED BLOCKS, THIS STARTS WORK ON THE TOP BLOCK
	public void startCurrentWork() {
		if (currentWork == null && startProcessingVBs) {
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
//ALL 3 SERVERS SHARE THINGS IN COMMON - WE PASS A "WORKER" TO DO SPECIFIC PROCESSING
class BlockServer extends Thread {
	int port;					
	Socket socket;
	ServerSocket serverSocket;
	IWorker worker;
	boolean run = true;			//We keep running until run is false
	BlockHolder blockHolder;
	
	public BlockServer(int _port, String ProcessID, IWorker _worker, BlockHolder _blockHolder) {	
		//SET THE PORT AND OUR WORKER
		this.port = Integer.valueOf(_port);// + ProcessID);
		this.blockHolder = _blockHolder;
		
		try {
			this.serverSocket = new ServerSocket(port, 6);
			this.worker = _worker;
		} catch (IOException e) {
			e.printStackTrace();
		}
		MessageUtils.writeToLog("Kim Ross's version of BlockChain, adapted from Clark Elliot's Inet server 1.1 - listening on port " + port, _blockHolder.intProcessID);
	}	
	public void run() {
		//PERFORM ONE-TIME SETUP (WHATEVER THAT MEANS FOR YOUR SERVER'S PURPOSE)
		worker.initialize();
		
		//LISTEN FOR COMMUNICATIONS FROM THE CONSORTIUM
		while (worker.keepRunning()) { 
			try {		
				socket = serverSocket.accept();
				worker.setSocket(socket);
				worker.run();
			} catch (IOException e) {
				MessageUtils.writeToLog(e.getMessage(), blockHolder.intProcessID);
			}	
		}
		//THIS SERVER MUST BE DONE WITH ITS WORK - CLOSE UP THE SOCKET		
		try {
			MessageUtils.writeToLog("Closing the server on port " + port + " - its work is done.", blockHolder.intProcessID);
			socket.close();
		} catch (NullPointerException nullex) {
			MessageUtils.writeToLog("Couldn't close the socket, it wasn't initialized yet.", blockHolder.intProcessID);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

/*
 * EACH SERVER DOES DIFFERENT WORK
 * AN INTERFACE LETS EACH KIND OF SERVER HAVE ITS OWN WORKER BY COMPOSITION
 */
interface IWorker  {
	public void start();
	public void run();
	public void setSocket(Socket _socket);
	//ONE-TIME SETUP
	public void initialize();
	//HOW DO I KNOW I SHOULD KEEP RUNNING?
	public Boolean keepRunning();
	
}
//PROCESSES KEY EXCHANGES BETWEEN CONSORTIUM MEMBERS
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
	//ONLY PROCESS 2 STARTS THE KEY-EXCHANGE PROCESS.
	public void initialize() {
		if (blockHolder.intProcessID == 2) {
			//I need to tell the servers to start exchanging keys!
			for (int i = 0; i < blockHolder.numConsortiumMembers; i++) {
				MessageUtils.sendMessageToServer("START!", MessageUtils.parseTargetPort(blockHolder.KEY_PORT_PREFIX, i), blockHolder.intProcessID);
			}
		}
	}
	//WE KEEP RUNNING UNTIL ALL KEYS WERE EXCHANGED, THEN WE CAN SHUT DOWN.
	public Boolean keepRunning() {
		return !blockHolder.hasAllKeys();
	}
	public void run() {
		PrintStream out = null;
		BufferedReader in = null;
		String whatFailed = "";
		try {
			//PIPES FOR INCOMING AND OUTGOING DATA
			in = new BufferedReader (new InputStreamReader(socket.getInputStream()));
			out = new PrintStream(socket.getOutputStream());
			
			try {
				StringBuilder sb = new StringBuilder();
				String result = "";
				
				//READING KEYS FROM CONSORTIUM MEMBERS
				while ((result = in.readLine()) != null) {
					sb.append(result);
				}					

				//THE 'START!' FLAG TELLS CONSORTIUM MEMBERS TO START THE EXCHANGE
				//THIS MAKES SURE ALL SERVERS ARE RUNNING AND READY BEFORE KEYS ARE SENT
				if (blockHolder.startProcessingKeys == false && sb.toString().contentEquals("START!")) {
					blockHolder.startProcessingKeys = true;
					KeyUtils.sendKeys(blockHolder);
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
					blockHolder.addKey(key,  processId, gsonParser, blockHolder.intProcessID);
				}
			}catch (NumberFormatException numex) {
				MessageUtils.writeToLog("Server received an invalid key from a consortium member: {" + whatFailed + "} on port " + this.socket.getLocalPort(), blockHolder.intProcessID);
			}catch (IOException iox) {
				//If we're here, we couldn't read incoming data from the client
				MessageUtils.writeToLog("Key Server encountered a read-error: " + iox.getMessage(), blockHolder.intProcessID);
			}
			socket.close();
		}catch (IOException ioe) {
			//Accessing the stream in either direction, in or out, can throw an IOException
			MessageUtils.writeToLog("Server encountered a read or write error:  " + ioe.getMessage(), blockHolder.intProcessID);
		}
	}
}
//THIS WORKER PROCESSES UNVERIFIED BLOCKS (FROM FILES OR THE CONSORTIUM)
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
	//THIS WAITS UNTIL KEYS WERE EXCHANGED
	public void initialize()  {
		if (!blockHolder.wereUBsSent) {
			while (blockHolder.startProcessingUBs == false) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			//ONCE WE HAVE ALL THE KEYS, WE READ OUR UNVERIFIED BLOCKS FROM FILE 
			//AND SEND THEM TO THE CONSORTIUM
			Gson gsonParser = new GsonBuilder().setPrettyPrinting().create();
			sendMyUBs(blockHolder.intProcessID, blockHolder, gsonParser);
		}
	}
	public Boolean keepRunning() {
		//if we're processing VERIFIED blocks, then we must have all the unverified ones.
		return !blockHolder.startProcessingVBs;
	}
	//SENDS THE UNVERIFIED BLOCKS FROM MY FILE TO THE CONSORTIUM
	private void sendMyUBs(int intProcessID, BlockHolder blockHolder, Gson gsonParser) {
		PriorityQueue<MedicalBlock> fileUBs = readBlocksFromFile("BlockInput" + intProcessID + ".txt", KeyUtils.getMyPrivateKey(blockHolder));
		
		for (int i = 0; i < blockHolder.numConsortiumMembers; i++) {	
			MessageUtils.sendMessageToServer(gsonParser.toJson(fileUBs), MessageUtils.parseTargetPort(blockHolder.UB_PORT_PREFIX, i), blockHolder.intProcessID);
		}		
		blockHolder.wereUBsSent = true;
	}
	//READS BLOCKS FROM THE TEXT FILE
	private PriorityQueue<MedicalBlock> readBlocksFromFile(String fileName, PrivateKey privateKey) {
		ArrayList<String> ret = new ArrayList<String>();
		Scanner scanner = null;
		try {
			java.io.File file = new java.io.File(System.getProperty("user.dir") + "\\" + fileName);
			MessageUtils.writeToLog("Reading blocks from text file " + file.getAbsolutePath(), blockHolder.intProcessID);
			scanner = new Scanner(file);
    	
			while (scanner.hasNextLine()) {
				ret.add(scanner.nextLine());
			}
				
		} catch (FileNotFoundException e) {
			MessageUtils.writeToLog(e.getMessage(), blockHolder.intProcessID);
		} finally {
			if (scanner != null) scanner.close();
		}
		PriorityQueue<MedicalBlock> ub = new PriorityQueue<MedicalBlock>();

		for (int i = 0; i < ret.size(); i++) {
			ub.add(new MedicalBlock(ret.get(i), blockHolder.intProcessID + "", privateKey));
		}
		return ub;
	}
	public void run() {
		MessageUtils.writeToLog("Unverified Block Worker is running...", blockHolder.intProcessID);
		BufferedReader in = null;
		
		try {
			//PIPES FOR INCOMING AND OUTGOING MESSAGES
			in = new BufferedReader (new InputStreamReader(socket.getInputStream()));
			//out = new PrintStream(socket.getOutputStream());
			
			try {
				StringBuilder sb = new StringBuilder();
				String result = "";
				
				//READING UNVERIFIED BLOCKS FROM THE CONSORTIUM
				while ((result = in.readLine()) != null) {
					sb.append(result);
				}					
				
				//ADDING UNVERIFIED BLOCKS TO THE UB CHAIN
				updateUnverifiedBlocks(sb.toString());
				
			}catch (NumberFormatException numex) {
				MessageUtils.writeToLog("Server received an invalid joke/proverb index", blockHolder.intProcessID);
			}catch (IOException iox) {
				//If we're here, we couldn't read incoming data from the client
				MessageUtils.writeToLog("Server encountered a read-error: " + iox.getMessage(), blockHolder.intProcessID);
			}
			socket.close();
		}catch (IOException ioe) {
			//Accessing the stream in either direction, in or out, can throw an IOException
			MessageUtils.writeToLog("Server encountered a read or write error:  " + ioe.getMessage(), blockHolder.intProcessID);
		}
	}
	//PARSES JSON RECEIVED FROM THE CONSORTIUM AND ADDS TO OUR UNVERIFIED-BLOCK QUEUE
	private void updateUnverifiedBlocks(String json) {
		//GSON needs to know what type of object to return
		java.lang.reflect.Type ubArray = new TypeToken<ArrayList<MedicalBlock>>() {}.getType();
		
		//Convert the json string into an ArrayList
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		ArrayList<MedicalBlock> incomingUBs = gson.fromJson(json, ubArray);
		
		//And add them to our Unverified block queue
		if (incomingUBs != null) blockHolder.addNewUBs(incomingUBs);	
	}
}
//THIS WORKER PROCESSES VERIFIED BLOCKS
class VerifiedBlockWorker extends Thread implements IWorker {
	Socket socket;
    BlockHolder blockHolder;
	
	VerifiedBlockWorker (BlockHolder _blockHolder) { //ModeHolder _modeHolder) {
		this.blockHolder = _blockHolder;
	}
	//THIS KEEPS US SLEEPING UNTIL UNVERIFIED BLOCKS ARE ALL COLLECTED
	public void initialize()  {
		while (blockHolder.startProcessingVBs == false) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		//CONSORTIUM MEMBERS COMMUNICATE THAT THEY HAVE ALL THE UNVERIFIED BLOCKS
		MessageUtils.sendMessageToServer("START!", MessageUtils.parseTargetPort(blockHolder.BLOCK_PORT_PREFIX, blockHolder.intProcessID), blockHolder.intProcessID);
	}
	//KEEP RUNNING UNTIL ALL UNVERIFIED BLOCKS ARE PROCESSED
	public Boolean keepRunning() {
		return !blockHolder.weAreDone;
	}
	@Override
	public void setSocket(Socket _socket) {
		this.socket = _socket;
	}
	public void run() {
		PrintStream out = null;
		BufferedReader in = null;
		try {			
			//PIPES FOR INCOMING AND OUTGOING MESSAGES
			in = new BufferedReader (new InputStreamReader(socket.getInputStream()));
			out = new PrintStream(socket.getOutputStream());
			
			try {
				StringBuilder sb = new StringBuilder();
				String result = "";
				
				//READING VERIFIED BLOCKS FROM THE CONSORTIUM
				while ((result = in.readLine()) != null) {
					sb.append(result);
				}					
				//WE'RE EITHER GETTING A BLOCK OR THE WORDS "START!" OR "DONE!"
				if (sb.toString().contentEquals("START!")) {
					//We accept these without processing a block (there isn't one in this message)
					blockHolder.startProcessingVBs = true;
				}else if (sb.toString().contentEquals("DONE!")) {
					blockHolder.weAreDone = true;
				}else {					
					//ADDING BLOCKS VERIFIED BY SOMEONE ELSE TO THE BLOCKCHAIN (with some verification...)
					String senderProcID = sb.toString().substring(0, 1);
					String jsonBlock = sb.toString().substring(1, sb.toString().length());
					String blockID = updateVerifiedBlocks(jsonBlock);
				}

			}catch (NumberFormatException numex) {
				MessageUtils.writeToLog("Server received an invalid joke/proverb index", blockHolder.intProcessID);
			}catch (IOException iox) {
				//If we're here, we couldn't read incoming data from the client
				MessageUtils.writeToLog("Server encountered a read-error: " + iox.getMessage(), blockHolder.intProcessID);
			}
			socket.close();
		}catch (IOException ioe) {
			//Accessing the stream in either direction, in or out, can throw an IOException
			MessageUtils.writeToLog("Server encountered a read or write error:  " + ioe.getMessage(), blockHolder.intProcessID);
		}
	}
	//SOLVED BLOCKS NEED TO GO ONTO THE BLOCKCHAIN
	private String updateVerifiedBlocks(String json)  {
		//GSON needs to know what type of object to return
		java.lang.reflect.Type ub = new TypeToken<MedicalBlock>() {}.getType();
		
		//Convert the json string into a MedicalBlock
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		MedicalBlock newBlock = gson.fromJson(json, ub);	
		MessageUtils.writeToLog("We received a solved block for " + newBlock.Fname + " " + newBlock.Lname + " (from " + newBlock.VerificationProcessID + ")", blockHolder.intProcessID);
		
		//validate it yourself... make sure it's good
		boolean isValid = false;
		try {
			isValid = newBlock.validate(blockHolder);
		} catch (NoSuchAlgorithmException e) {
			MessageUtils.writeToLog("Encryption not supported:  " + e.getMessage(), blockHolder.intProcessID);
		}
				
		//Add to the chain
		if (isValid) {
			blockHolder.stopCurrentWork();
			blockHolder.blockChain.add(newBlock);
			blockHolder.ub.remove(newBlock); //make sure we don't process this block if we're late getting to it		
		}else {
			MessageUtils.writeToLog("<<<<<<<<<<< THIS BLOCK DOESN'T PASS VALIDATION!!!>>>>>>>>>>", blockHolder.intProcessID);
		}
				
		if (isValid) {
			return newBlock.BlockID;
		}else {
			return "";
		}	
	}
}
//THE BLOCKWORKER TRIES TO SOLVE PUZZLES (I.E. DO WORK)
class BlockWorker extends Thread {
	MedicalBlock currentBlock;
	BlockHolder blockHolder;
	
	public BlockWorker(MedicalBlock _ub, BlockHolder _blockHolder) {
		this.currentBlock = _ub;
		blockHolder = _blockHolder;
	}
	public void run() {
		try {
			MessageUtils.writeToLog("\n8888888888888888888888888888888888888888888888888888888888888888", blockHolder.intProcessID);
			MessageUtils.writeToLog("Working on " + currentBlock.Fname + " " + currentBlock.Lname, blockHolder.intProcessID);

			//Fake-work courtesy of Clark Elliott's blockj sample code
			int randval = 27; // arbitrary value
			int tenths = 0;
			Random r = new Random();
			for (int i=0; i<1000; i++){ // upper limit
				Thread.sleep(100); // resting to simulate that we're working hard
				randval = r.nextInt(100); // a value not too time-consuming to process
				MessageUtils.writeToLog(".", blockHolder.intProcessID,"");
				if (randval < 4) {       // we consider this a "winner" if it's less than 4.
					tenths = i;
					break;
				}
			}
			MessageUtils.writeToLog("\n <-- We did " + tenths + " tenths of a second of *work*.\n", blockHolder.intProcessID);

			//Set the winning answer for our fake work
			currentBlock.RandomSeed = randval + "";
			
			//Give credit to the winner
			currentBlock.VerificationProcessID = blockHolder.intProcessID + "";
			
			//Set the Winning Hash from the previous solved block
			currentBlock.PreviousHash = blockHolder.blockChain.getTopHash(blockHolder.intProcessID);

			//Hash it together to make the new WinningHash
			String toHash = currentBlock.PreviousHash + randval + currentBlock.toString();
			String hashed = KeyUtils.hashIt(toHash);
			currentBlock.WinningHash = hashed;			
			currentBlock.SignedWinningHash = KeyUtils.signThis(hashed.getBytes(), KeyUtils.getMyPrivateKey(blockHolder));

			//SEND THIS BLOCK AS JSON TO THE CONSORTIUM MEMBERS
			Gson gsonParser = new GsonBuilder().setPrettyPrinting().create();
			MessageUtils.sendMessageToConsortium(blockHolder.intProcessID + gsonParser.toJson(currentBlock), blockHolder.numConsortiumMembers, blockHolder.BLOCK_PORT_PREFIX, blockHolder.intProcessID);
			MessageUtils.writeToLog("I SOLVED " + currentBlock.Fname + " " + currentBlock.Lname + "!  Sending to consortium.", blockHolder.intProcessID);
		}catch (InterruptedException ie) {
			MessageUtils.writeToLog("******** Interrupted! Move on to another block *********", blockHolder.intProcessID);
		}catch (NoSuchAlgorithmException ex) {
			MessageUtils.writeToLog("Encryption method not supported:  " + ex.getMessage(), blockHolder.intProcessID);
		}
	}
}
//THIS REPRESENTS A BLOCK IN THE BLOCKCHAIN
class MedicalBlock implements Comparable {
	  String BlockID;
	  String VerificationProcessID;	//PROCESS ID THAT SOLVED THIS PUZZLE
	  String PreviousHash; 			//HASH OF THE PREVIOUS BLOCK, ONCE THIS IS SOLVED
	  String Fname;
	  String Lname;
	  String DOB;
	  String SSNum;
	  String Diag;
	  String Treat;
	  String Rx;
	  String RandomSeed; 			// THE WINNING NUMBER THAT SOLVES THE PUZZLE
	  String WinningHash;			// THE PREVIOUS-HASH + RANDOMSEED + DATA IN THIS BLOCK, HASHED TOGETHER
	  byte[] SignedWinningHash;		// THE SIGNED WINNING HASH
	  String timeStamp;				// FOR SORTING - WE PROCESS BLOCKS IN ORDER
	  String CreatorProcessID;		// THE PROCESS ID THAT CREATED THIS BLOCK INITIALLY
	  byte[] signedByCreator;		// THE BLOCKID SIGNED BY THE CREATOR

	  //PARSES THE TEXT FILE STRING INPUT INTO FIELDS, SETS THE VALUES IN ORDER.
	public MedicalBlock(String fileInput, String procID, PrivateKey privateKey) {
		  String[] vals = fileInput.split(" ");
		  BlockID = UUID.randomUUID().toString();
		  signedByCreator = KeyUtils.signThis(BlockID.getBytes(), privateKey);
		  Fname = vals[0];
		  Lname = vals[1];
		  DOB = vals[2];
		  SSNum = vals[3];
		  Diag = vals[4];
		  Treat = vals[5];
		  Rx = vals[6];
		  CreatorProcessID = procID;
		  
		  //Courtesy of Clark Elliot's BlockJ example
		  Date date = new Date();
		  String T1 = String.format("%1$s %2$tF.%2$tT", "", date); // FORMATS A TIME STAMP THAT GSON LIKES	  
		  timeStamp = T1 + "." + procID; 
		  VerificationProcessID = "";
	  }
	//THIS CONSTRUCTOR TAKES THE PRIVATE KEY OF THE CREATOR OF THE BLOCK (FOR SIGNING)
	public MedicalBlock(PrivateKey privateKey) throws NoSuchAlgorithmException { //For initial fake block;
		BlockID = UUID.randomUUID().toString();
		signedByCreator = KeyUtils.signThis(BlockID.getBytes(), privateKey);
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
		String T1 = String.format("%1$s %2$tF.%2$tT", "", date); // TIME STAMP GSON LIKES	  
		timeStamp = T1 + "." + 0; 
		RandomSeed = "0";
		String toHash = "1" + "0" + this.toString();
		String encrypted = KeyUtils.hashIt(toHash);
		WinningHash = encrypted;
		SignedWinningHash = KeyUtils.signThis(WinningHash.getBytes(), privateKey);
	}
	//TURNS THIS BLOCK INTO JSON
	public String toJson() {
		  GsonBuilder gb = new GsonBuilder();
		  gb.setPrettyPrinting();
		  Gson gson = gb.create();
		  return gson.toJson(this);
	  }
	@Override
	//USED BY THE PRIORITY QUEUE THIS WILL GO INTO
	//DETERMINES THE ORDER OF THE BLOCKS SO THEY'RE CONSISTENT ACROSS THE CONSORTIUM
	public int compareTo(Object o) {
		MedicalBlock m = (MedicalBlock)o;
		return this.timeStamp.compareTo(m.timeStamp);		
	}
	//ONCE SOLVED, VALIDATES THE AUTHENTICITY OF THIS BLOCK.
	public boolean validate(BlockHolder blockHolder) throws NoSuchAlgorithmException {
		MessageUtils.writeToLog("VALIDATING BLOCK:", blockHolder.intProcessID);
		
		//Make sure when re-hashing, we get the same value
		String toCheck = PreviousHash + RandomSeed + this.toString();
		String hashed = KeyUtils.hashIt(toCheck);
		boolean validHash = hashed.contentEquals( WinningHash);
		
		//Verify the winning-process ID's signature
		int winnerProc = Integer.valueOf(this.VerificationProcessID);
		PublicKey winnersKey = blockHolder.getKey(winnerProc);
		boolean validWinner = KeyUtils.verifySignature(WinningHash.getBytes(), winnersKey, SignedWinningHash);
				
		//Verify the signed blockID of the creating process
		int creatorProcID = Integer.valueOf(this.CreatorProcessID);
		PublicKey creatorsPublicKey = blockHolder.getKey(creatorProcID);		
		boolean validCreationSignature = KeyUtils.verifySignature(BlockID.getBytes(), creatorsPublicKey, signedByCreator);
		
		//Output our findings
		MessageUtils.writeToLog("\tValid Hash? " + validHash, blockHolder.intProcessID);
		MessageUtils.writeToLog("\tValid Winner Signature? " + validWinner, blockHolder.intProcessID);
		MessageUtils.writeToLog("\tValid Creator's Signature? " + validCreationSignature, blockHolder.intProcessID);
		if (validHash && validWinner && validCreationSignature) {
			MessageUtils.writeToLog("<<< VALID BLOCK! >>>", blockHolder.intProcessID);
		}else {
			MessageUtils.writeToLog("<<< INVALID BLOCK!!! >>>", blockHolder.intProcessID);
		}
		
		return validHash && validWinner && validCreationSignature;
	}
	@Override
	//This is important because it is used to extract the "data" from the block for hashing
	public String toString() {
		String s = "|";
		return "F=" +Fname + ",L=" +Lname + ",B=" + BlockID + ",D="+ DOB + ",S=" + SSNum + ",DTR=" + Diag + Treat + Rx + ",V=" +VerificationProcessID + ",PH=" + PreviousHash;  
	}
	//CONVENIENCE FOR PRINTING BASIC BLOCK INFORMATION WHEN LOGGING
	public String toTitle() {
		return Fname + " " + Lname;
	}
}	


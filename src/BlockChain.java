import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Time;
import java.util.ArrayList;
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

public class BlockChain {
	
	public static void main(String[] args) {
		String ProcessID = "0";
		if (args.length > 0) ProcessID = args[0];
		PriorityQueue<MedicalBlock> ub = readBlocksFromFile("BlockInput" + ProcessID + ".txt");
		
		Iterator it = ub.iterator();
		while (it.hasNext()) {
			MedicalBlock thisBlock = (MedicalBlock)it.next();
			System.out.println(thisBlock.toJson());
		}
		
	}
	
	public static PriorityQueue<MedicalBlock> readBlocksFromFile(String fileName) {
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
		PriorityQueue<MedicalBlock> ub = new PriorityQueue<MedicalBlock>();
		for (int i = 0; i < ret.size(); i++) {
			ub.add(new MedicalBlock(ret.get(i)));
		}
		return ub;
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
	  //Date timeStamp;
	  
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
	  }
	  
	  public String toJson() {
		  GsonBuilder gb = new GsonBuilder();
		  gb.setPrettyPrinting();
		  Gson gson = gb.create();
		  return gson.toJson(this);
	  }

	@Override
	public int compareTo(Object o) {
		return 0;
	}
}	


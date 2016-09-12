package transaction;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import event.*;
import iogenerator.*;

public abstract class Transaction implements Runnable {
	
	public Window window;
	HashSet<String> results;
	OutputFileGenerator output;
	public CountDownLatch transaction_number;	
	AtomicLong total_cpu;
	AtomicInteger total_mem;
	
	public Transaction (Window w, OutputFileGenerator o, CountDownLatch tn, AtomicLong time, AtomicInteger mem) {		
		window = w;		
		results = new HashSet<String>();
		output = o; 
		transaction_number = tn;
		total_cpu = time;
		total_mem = mem;
	}	
	
	public int getEventNumber (String sequence) {
		int number = 0;
		for (int i=0; i<sequence.length(); i++) {
			if (sequence.substring(i,i+1).equals(";")) number++;
		}
		return number;
	}
}

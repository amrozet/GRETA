package scheduler;

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import event.*;
import iogenerator.*;
import transaction.*;
import query.*;

public class Scheduler implements Runnable {
	
	final EventQueue eventqueue;
	int firstsec;
	int lastsec;
		
	String algorithm;
		
	Query query;
	ArrayDeque<Window> windows;
	
	ExecutorService executor;
	AtomicInteger drProgress;
	CountDownLatch transaction_number;
	CountDownLatch done;
	
	AtomicLong total_cpu;	
	AtomicInteger total_memory;
	OutputFileGenerator output;
	
	public Scheduler (EventQueue eq, int first, int last, String algo,
			String ess, String pred, int wl, int ws,
			ExecutorService exe, AtomicInteger dp, CountDownLatch d, AtomicLong time, AtomicInteger mem, OutputFileGenerator o) {	
		
		eventqueue = eq;
		firstsec = first;
		lastsec = last;
		
		algorithm = algo;
		
		query = new Query(ess,pred,wl,ws);
		windows = new ArrayDeque<Window>();
						
		executor = exe;
		drProgress = dp;
		int window_number = (last-first)/ws + 1;
		transaction_number = new CountDownLatch(window_number);
		done = d;
		
		total_cpu = time;
		total_memory = mem;
		output = o;	
	}
	
	/**
	 * As long as not all events are processed, extract events from the event queue and execute them.
	 */	
	public void run() {	
		
		/*** Create windows ***/	
		ArrayDeque<Window> windows2iterate = new ArrayDeque<Window>();
		int start = firstsec;
		int end = query.window_length;
		while (start <= lastsec) {
			Window window = new Window(start, end);		
			windows.add(window);
			windows2iterate.add(window);
			start += query.window_slide;
			end = (start+query.window_length > lastsec) ? lastsec : (start+query.window_length); 
			//System.out.println(window.toString() + " is created.");
		}			
		
		/*** Set local variables ***/
		int progress = Math.min(query.window_slide,lastsec);
		boolean last_iteration = false;
									
		/*** Get the permission to schedule current slide ***/
		while (eventqueue.getDriverProgress(progress)) {
			
			/*** Schedule the available events ***/
			Event event = eventqueue.contents.peek();
			while (event != null && event.sec <= progress) { 
					
				Event e = eventqueue.contents.poll();
				
				/*** Fill windows with events ***/
				for (Window window : windows2iterate) {
					if (window.relevant(e)) window.events.add(e); 
				}
				/*** Poll an expired window and submit it for execution ***/
				if (!windows2iterate.isEmpty() && windows2iterate.getFirst().expired(e)) {					
					Window window = windows2iterate.poll();
					if (window.events.size() > 1) {
						System.out.println(window.toString());
						execute(window);				
					} else {
						transaction_number.countDown();
					}
				}
				event = eventqueue.contents.peek();
			}		 
			/*** Update progress ***/
			if (last_iteration) {
				break;
			} else {
				if (progress+query.window_slide>lastsec) {
					progress = lastsec;
					last_iteration = true;
				} else {
					progress += query.window_slide;
				}
			}									
		}
		/*** Poll the last windows and submit them for execution ***/
		for (Window window : windows2iterate) {
			if (window.events.size() > 1) {
				System.out.println(window.toString());
				execute(window);			
			} else {
				transaction_number.countDown();
			}
		}		
		/*** Terminate ***/
		try { transaction_number.await(); } catch (InterruptedException e) { e.printStackTrace(); }
		done.countDown();	
		//System.out.println("Scheduler is done.");
	}	
	
	public void execute(Window window) {
		
		Transaction transaction;
		if (algorithm.equals("eta")) {
			transaction = new ETA(window,query,output,transaction_number,total_cpu,total_memory);
		} else {
		if (algorithm.equals("aseq")) {
			transaction = new Aseq(window,output,transaction_number,total_cpu,total_memory);
		} else {
		if (algorithm.equals("sase")) {
			transaction = new Sase(window,output,transaction_number,total_cpu,total_memory);
		} else {
			transaction = new Echo(window,output,transaction_number,total_cpu,total_memory);
		}}}
		executor.execute(transaction);	
	}	
}
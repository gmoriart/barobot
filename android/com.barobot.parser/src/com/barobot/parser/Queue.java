package com.barobot.parser;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;


import com.barobot.parser.message.AsyncMessage;
import com.barobot.parser.output.AsyncDevice;


/*
 * LinkedList<E>
    get(int index) is O(n)
    add(E element) is O(1)
    add(int index, E element) is O(n)
    remove(int index) is O(n)
    Iterator.remove() is O(1) <--- main benefit of LinkedList<E>
    ListIterator.add(E element) is O(1) <--- main benefit of LinkedList<E>

ArrayList<E>
    get(int index) is O(1) <--- main benefit of ArrayList<E>
    add(E element) is O(1) amortized, but O(n) worst-case since the array must be resized and copied
    add(int index, E element) is O(n - index) amortized, but O(n) worst-case (as above)
    remove(int index) is O(n - index) (i.e. removing last is O(1))
    Iterator.remove() is O(n - index)
    ListIterator.add(E element) is O(n - index)
 */

public class Queue {
	public static final int DFAULT_DEVICE = 0;

	private final Object lock = new Object();
	private static LinkedList<AsyncDevice> devs = new LinkedList<AsyncDevice>();
	private LinkedList<AsyncMessage> output = new LinkedList<AsyncMessage>();
	private int wait_for_device_id = -1;
	public static int registerSource( AsyncDevice dev ) {
		synchronized(devs){
			devs.add(dev);
			return devs.size() - 1;
		}
	}
	public static AsyncDevice getDevice(int mainboardSource) {
		return devs.get(mainboardSource);
	}
	public void clear(int devindex ) {
		synchronized (this.lock) {
			AsyncDevice dev = Queue.getDevice(devindex);
			dev.unlockRet("<close>");
			this.output.clear();
		}
	}
	public void read(int mainboardSource, String in) {
		//synchronized (this.lock) {
			devs.get(mainboardSource).read( in );
		//}
	}
	public void add(final int mainboardSource, final String cmd, boolean blocking) {
		synchronized (this.lock) {
			if(blocking){
				final String retcmd = "R" + cmd;
				output.add(new AsyncMessage( cmd, blocking ){
					public boolean isRet(String result) {
						if( retcmd.equals(result)){
							Parser.logger.log(Level.INFO, "isret: " + result+" for "  + cmd);
							return true;
						}
						return false;
					}
					public int getDeviceId() {
						return mainboardSource;
					}
				});
			}else{
				output.add(new AsyncMessage( cmd, blocking ));
			}
		}
		run();
	}
	public void add( String cmd, boolean blocking) {
		synchronized (this.lock) {
			if(blocking){
				final String retcmd = "R" + cmd;
				output.add(new AsyncMessage( cmd, blocking ){
					public boolean isRet(String result) {
						if( retcmd == result){
							return true;
						}
						return false;
					}
				});
			}else{
				output.add(new AsyncMessage( cmd, blocking ));
			}
		}
		run();
	}

	public void add(AsyncMessage asyncMessage) {
		synchronized (this.lock) {
			output.add(asyncMessage);
		}
		run();
	}

	public void add(Queue q2) {
		synchronized (this.lock) {
			output.addAll(q2.output);
		}
		run();
	}	
	private void run() {
		synchronized (this.lock) {
			if(this.wait_for_device_id >= 0){
			//	Constant.log("wait_for1", wait_for_device.name);
				return;		// jestem w trakcie oczekiwania
			}
	//		boolean wasEmpty = output.isEmpty();
			while (!output.isEmpty()) {
				if(this.wait_for_device_id >= 0){
				//	Constant.log("wait_for2", wait_for_device.name);
					return;
				}
				AsyncMessage m = output.pop();
				AsyncDevice dev = m.getDevice();
				if(dev!=null){
					m.start( dev );
					moveToHistory( m );
					if(m.isBlocing()){
						System.out.println("run run:" + "isBlocing " + m.command );
	                	this.wait_for_device_id	= m.getDeviceId();
	                	dev.waitFor(m, this );
	                	return;
	                }else{
	                	this.wait_for_device_id	= -1;
	                }
				}
			}
			if(output.isEmpty()){
				System.out.println("queue empty");
			}
		}
	}

	public void addFirst(Queue q2) {
		synchronized (this.lock) {
			this.output.addAll( 0, q2.output);		// add on start	
		}
		run();
	}

    public synchronized void sendNow( int devindex, String command ) throws IOException {	 // send without waiting
		synchronized (this.lock) {
			devs.get(devindex).send(command);
		}
    }
	public void unlock() {
		synchronized (this.lock) {
			this.wait_for_device_id = -1;
		}
		Parser.logger.log(Level.INFO, "unlock()");
		run();
	}

	private void moveToHistory(AsyncMessage m) {
		// TODO Auto-generated method stub
	}
	
	public void addWaitThread(final Object thread) {
	//	Parser.logger.log(Level.INFO, "add thread wait");
		this.add( new AsyncMessage( true ){
			@Override
			public void run(AsyncDevice dev) {
	//			Parser.logger.log(Level.INFO, "thread notify");
				synchronized(thread){
					thread.notify();
				}
			}
		});
	//	Parser.logger.log(Level.INFO, "START wait");
		synchronized(thread){
			try {
				thread.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	//	Parser.logger.log(Level.INFO, "end wait");
	}

	public void addWait(final int time) {
		final AsyncMessage m2 = new AsyncMessage( true ) {
			public void run(AsyncDevice dev) {
				this.name				= "wait " + time;
			}
			@Override
			public long getTimeout() {
				return time;
			}
			@Override
			public boolean isBlocing() {
				return true;
			}
			@Override
			public boolean isRet(String result) {
				return false;
			}
			@Override
			public void afterTimeout() {
				this.unlocking_command = "end";
			}
		};
		output.add( m2 );		
	}
	public static void disableDevice(int mainboardSource) {
		AsyncDevice dev = getDevice(mainboardSource);
		dev.disable();
	}
	public static void enableDevice(int mainboardSource) {
		AsyncDevice dev = getDevice(mainboardSource);
		dev.enable();
	}
}




/*
final Handler handler	= new Handler();
handler.postDelayed(new Runnable() {
  @Override
  public void run() {
	  ar.unlock( m3 );
  }
}, time);			

new Timer().schedule(new TimerTask() {          
    public void run() {
    	 ar.unlock( m3 );
    }
}, time);// odczekaj tyle czasu, odblokuj kolejk� i jedz dalej
return null;

*/

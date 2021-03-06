package com.phoenix.nattester;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.phoenix.nattester.DefaultAsyncProgress.AsyncTaskListener;
import com.phoenix.nattester.MainFragmentActivity.ViewPagerVisibilityListener;
import com.phoenix.nattester.benchmark.BenchTask;
import com.phoenix.nattester.benchmark.BenchTaskParam;
import com.phoenix.nattester.random.RandomTask;
import com.phoenix.nattester.random.RandomTaskParam;
import com.phoenix.nattester.service.IServerService;
import com.phoenix.nattester.service.IServerServiceCallback;
import com.phoenix.nattester.service.ReceivedMessage;
import com.phoenix.nattester.service.ServerService;

public class ParametersFragment extends SherlockFragment implements 
	AsyncTaskListener, ViewPagerVisibilityListener, OnSharedPreferenceChangeListener {
	  private static final Logger LOGGER = LoggerFactory.getLogger(ParametersFragment.class);
	  public final static String TAG = "ParFragment";
	  
	  protected Button btnGetPublicIP;
	  protected Button btnNATDetect;
	  protected Button btnAbort;
	  protected Button btnAlg;
	  protected EditText ePublicIP;
	  protected EditText eLocalIP;
	  protected EditText eTxName;
	  protected EditText eTxServer;
	  protected ProgressBar pbar;
	  protected TextView pmsg;
	  private InternalProgress iproc;

	  // static application config
	  final TaskAppConfig cfg = new TaskAppConfig();
	  
	  protected ProgressDialog progressDialog;
	  protected final ArrayList<MessageObserver> mObservers = new ArrayList<MessageObserver>(8);
		
	  public final void updateCfgFromUI(){
			try {
				SharedPreferences sprefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        		String eNstr = sprefs.getString("symmetric_nat_traverse_ports", "25");
        		int eN = 0;
        		try {
        			eN = Integer.parseInt(eNstr);
        		} catch(NumberFormatException e){
        			LOGGER.warn("Invalid format, should be number", e);
        		}
        		
				cfg.setN(eN);
				cfg.setTxName(eTxName.getText().toString());
				cfg.setTxServer(eTxServer.getText().toString());
				//cfg.setPeerIP(ePeerIP.getText().toString());
				//cfg.setPeerPort(Integer.parseInt(ePeerPort.getText().toString()));
				
				cfg.setPublicIP(ePublicIP.getText().toString());
				cfg.setLocalIP(eLocalIP.getText().toString());
				
				cfg.setStunPort(3478);
				cfg.setStunServer("89.29.122.60");
				cfg.setUpdateCallback(this);
			} catch(Exception e){
				LOGGER.error("Problem during fetching app config", e);
			}
		}
	  
		// Implement public interface for the service
		private final IServerServiceCallback.Stub binder = new IServerServiceCallback.Stub() {
			@Override
			public void messageSent(int success) throws RemoteException {
				MainFragmentActivity act = (MainFragmentActivity) getActivity();
				act.messageSent(success);
			}
			@Override
			public void messageReceived(ReceivedMessage msg) throws RemoteException {
				LOGGER.debug("MsgReceived() from Stub()");
				
				// at first distribute message among observers
				synchronized(mObservers){
					Iterator<MessageObserver> it = mObservers.iterator();
					while(it.hasNext()){
						MessageObserver mob = it.next();
						if (mob==null) { it.remove(); continue; }
						
						try {
							mob.onNewMessageReceived(msg);
						} catch(Exception e){
							LOGGER.error("Exception during message received, observer failed", e);
						}
					}
				}
				
				// now pass this to the main activity
				MainFragmentActivity act = (MainFragmentActivity) getActivity();
				act.messageReceived(msg);
			}				
		};
		
		private IServerServiceCallback smallCallback = new IServerServiceCallback() {
			@Override
			public IBinder asBinder() {
				LOGGER.debug("AS binder? returning binder: " + binder);
				return binder;
			}
			@Override
			public void messageSent(int success) throws RemoteException {
				MainFragmentActivity act = (MainFragmentActivity) getActivity();
				act.messageSent(success);
			}
			@Override
			public void messageReceived(ReceivedMessage msg) throws RemoteException {
				LOGGER.debug("MsgReceived() from smallCallback");
				
				// at first distribute message among observers
				synchronized(mObservers){
					Iterator<MessageObserver> it = mObservers.iterator();
					while(it.hasNext()){
						MessageObserver mob = it.next();
						if (mob==null) { it.remove(); continue; }
						
						try {
							mob.onNewMessageReceived(msg);
						} catch(Exception e){
							LOGGER.error("Exception during message received, observer failed", e);
						}
					}
				}
				
				// now pass this to the main activity
				MainFragmentActivity act = (MainFragmentActivity) getActivity();
				act.messageReceived(msg);
			}
		};
		
		private IServerService api;
		private ServiceConnection serviceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				Log.i(TAG, "Service connection established");
				
				// that's how we get the client side of the IPC connection
				api = IServerService.Stub.asInterface(service);
				try {
					cfg.setApi(api);
					
					LOGGER.debug("Setting callback in activityXX: " + ParametersFragment.this.smallCallback);
					api.setCallback(ParametersFragment.this.smallCallback);
					api.startServer();
					
					// progress dialog is not needed now
					progressDialog.setMessage("Done");        		
					progressDialog.dismiss();
				} catch (RemoteException e) {
					LOGGER.error("Failed to add listener", e);
				}
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				Log.i(TAG, "Service connection closed");
				api=null;
			}
		};
		
		private void initProgress(String message, String title){
			progressDialog=new ProgressDialog(getActivity());
			progressDialog.setMessage(message);
			progressDialog.setTitle(title);
			progressDialog.setCancelable(true);
			progressDialog.setCanceledOnTouchOutside(false);
			progressDialog.setIndeterminate(true);
			progressDialog.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					LOGGER.debug("Progressbar canceled");
					getActivity().finish();
				}
			});
			
			progressDialog.setOnDismissListener(new OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					LOGGER.debug("Progressbar dismissed");
				}
			});
		}
		
	  @Override
	  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    // Inflate the layout for this fragment
	    View view = inflater.inflate(R.layout.prefs_fragment, container, false);
	    
	    
	    return view;
	  }
	  
	  @Override
	  public void onAttach(Activity activity) {
	  	  super.onAttach(activity);
	  	  
	  	    // service stuff
			LOGGER.debug("About to start service");
			
			Intent intent = new Intent(ServerService.class.getName());
			this.getActivity().getApplicationContext().startService(intent);
			this.getActivity().getApplicationContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
			
			// SharedPrefs changed
			SharedPreferences sprefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
			sprefs.registerOnSharedPreferenceChangeListener(this);
			
			// init progressbar that waits for service to bind
			this.initProgress("Initializing...", "Starting & connecting to service");
	  }
	
	  @Override
	  public void onDetach() {
		  super.onDetach();
		  LOGGER.debug("prefsDetached");
		  
		  SharedPreferences sprefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		  sprefs.unregisterOnSharedPreferenceChangeListener(this);
		  
		  try {
				LOGGER.debug("About to stop service");
				api.setCallback(null);
				this.getActivity().getApplicationContext().unbindService(serviceConnection);
				this.getActivity().getApplicationContext().stopService(new Intent(ServerService.class.getName()));
			} catch(Exception e){
				LOGGER.error("Exception during service stopping", e);
			}
	  }
	
	@Override
	  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		  super.onCreateOptionsMenu(menu, inflater);
		  inflater.inflate(R.menu.actionmenu, menu);
	  }
	
	  @Override
	  public void onPrepareOptionsMenu(Menu menu) {
		  super.onPrepareOptionsMenu(menu);
	  }
	
	  @Override
	  public boolean onOptionsItemSelected(MenuItem item) {
		  switch (item.getItemId()) {
	        case R.id.menu_settings:
	            LOGGER.debug("Options: settings");
	            
	            Intent intent = new Intent(getActivity(), PrefsActivity.class);
	            startActivity(intent);
	            
	            return true;
	        case R.id.menu_nat_timeout:
	        	LOGGER.debug("Options: NAT timeout");
	        	startNATtimeoutTask(1);
	            return true;
	        case R.id.menu_port_timeout:
	        	LOGGER.debug("Options: Port timeout");
	        	startNATtimeoutTask(2);
	            return true;
	        case R.id.menu_random:
	        	LOGGER.debug("Options: Random collection");
	        	startRandomTask(0);
	        	return true;
	        case R.id.menu_random_norecv:
	        	LOGGER.debug("Options: Random collection scan");
	        	startRandomTask(1);
	        	return true;
	        case R.id.menu_random_nostun:
	        	LOGGER.debug("Options: Random collection scan, noStun");
	        	startRandomTask(2);
	        	return true;
	        case R.id.menu_benchmark:
	        	LOGGER.debug("Options: Benchmark started");
	        	startBenchmarkTask(2);
	        	return true;
	        case R.id.menu_sampling:
	        	LOGGER.debug("Options: Sampling started");
	        	startSamplingTask();
	        	return true;
	        default:
	            return super.onOptionsItemSelected(item);
	     }
	  }


	@Override
	  public void onViewCreated(View view, Bundle savedInstanceState) {
		  super.onViewCreated(view, savedInstanceState);
		  this.setHasOptionsMenu(true);
		  LOGGER.debug("OnViewCreated - logger");
		
		  // initialize controls
	      btnGetPublicIP = (Button) view.findViewById(R.id.btnGetPublicIP);
		  btnNATDetect = (Button) view.findViewById(R.id.btnNATDetect);
		  btnAlg = (Button) view.findViewById(R.id.btnAlg);
		  btnAbort = (Button) view.findViewById(R.id.btnAbort);
		  ePublicIP = (EditText) view.findViewById(R.id.txtPublic);
		  eLocalIP = (EditText) view.findViewById(R.id.txtLocal);
		  eTxName = (EditText) view.findViewById(R.id.txtTXName);
		  eTxServer = (EditText) view.findViewById(R.id.txtTXServer);
		  pbar = (ProgressBar) view.findViewById(R.id.progressBar1);
		  pmsg = (TextView) view.findViewById(R.id.textVMessage);
		  pbar.setIndeterminate(false);
		  
		  iproc = new InternalProgress();
		  		
		
        // internal process canceller
        btnAbort.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				iproc.cancel();
			}
		});
        
        /** INITIALIZE BUTTON; get public IP **/
        btnGetPublicIP.setOnClickListener(new PublicIPistener());
        
        /** INITIALIZE BUTTON; NAT detect */
        btnNATDetect.setOnClickListener(new NATClickListener());
        
        /** INITIALIZE BUTTON; Traverse */
        btnAlg.setOnClickListener(new TraverseClickListener());
        
	        
	        /** INITIALIZE BUTTON; connect *
	        btnAlg.setOnClickListener(new View.OnClickListener() {
	            @Override
	            public void onClick(View v) {
	                    try{
	                    	final AlgTask task = new AlgTask(); 
	                    	ProgressDialog dialog = new ProgressDialog(MainActivity.this);
	                    	dialog.setIndeterminate(true);
	                    	dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	                        dialog.setCancelable(true);
	                        dialog.setMessage("Initializing");
	                        dialog.setTitle("Communicating...");
	                        dialog.setOnCancelListener(new OnCancelListener() {
								@Override
								public void onCancel(DialogInterface dialog) {
									task.cancel(false);
								}
							});
	                        dialog.show();
	                    	
	                    	task.setContext(MainActivity.this);
	                    	task.setDialog(dialog);
	                    	task.setCallback(MainActivity.this);
	                    	
	                    	// parameters
	                    	updateCfgFromUI();
	                    	
	                    	// execute async task
	                    	task.execute(cfg);
	                    }catch(Exception e){
	                    	Log.e(TAG, "Exception", e);
	                    }
	            }
	        });*/
	}
	  
	  public void taskActionsSetEnabled(boolean enabled){
			btnAlg.setEnabled(enabled);
			btnGetPublicIP.setEnabled(enabled);
			btnNATDetect.setEnabled(enabled);	
	  }
	  
	  /**
	   * Starts internal progressbar with corresponding message
	   */
	  private class InternalProgress implements AsyncTaskListener{
		  @SuppressWarnings("unused")
		  private boolean cancelable=true;
		  private boolean canceled=false;
		  private boolean running=false;
		  private boolean taskCancelled=false;
		  private OnCancelListener cancelListener=null;
		
		  public void start(){
			  canceled=false;
			  running=true;
			  taskCancelled=false;
			  
			  getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					taskActionsSetEnabled(false);
					pbar.setIndeterminate(true);
					pbar.invalidate();					
				}
			});
		  }
		  
		  public void stop(){
			  running=false;			  
			  getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						taskActionsSetEnabled(true);
						pbar.setIndeterminate(false);
						pbar.invalidate();					
					}
				});
		  }
		  
		  @SuppressWarnings("unused")
		  public boolean isCanceled(){ return canceled; }
		  @SuppressWarnings("unused")
		  public boolean isRunning(){ return running; }
		  public void setOnCancelListener(OnCancelListener cancelListener){
			  this.cancelListener = cancelListener;
		  }
		  
		  public synchronized void cancel(){ 
			  LOGGER.debug("Going to cancel some task");
			  if (canceled==false)
				  pmsg.setText(pmsg.getText()+ " - CANCELLED");
			  canceled = true;
			  if (cancelListener!=null){
				  if (!taskCancelled)
					  cancelListener.onCancel(null);
				  taskCancelled=true;
				  // no stop() call - wait for -1 message to stop
			  } else {
				  this.stop();
			  }
		  }
		  
		@Override
		public void onTaskUpdate(DefaultAsyncProgress progress, int state) {
			if (state==0){
				this.start();
			} else 	if (state==2){ // finish
				this.stop();
			} else if (state==-1){
				this.stop();
			} else {
				if (progress!=null)
					pmsg.setText(progress.getMessage());
				ParametersFragment.this.onTaskUpdate(progress, state);
			}
		}

		@Override
		public void setPublicIP(String IP) { }

		@Override
		public void setLocalIP(String IP) { }
	  }
	  
	  private class PublicIPistener implements View.OnClickListener {
		  @Override
          public void onClick(View v) {
              try{
              	final GetPublicIPTask task = new GetPublicIPTask();
              	
              	ProgressDialog dialog = new ProgressDialog(getActivity());
              	dialog.setIndeterminate(true);
              	dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                  dialog.setCancelable(true);
                  dialog.setMessage("Initializing");
                  dialog.setTitle("Obtaining public IP");
                  dialog.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							task.cancel(true);
						}
					});
                  
                  dialog.show();
              	
                   
              	task.setContext(getActivity().getApplicationContext());
              	task.setDialog(dialog);
              	task.setCallback(ParametersFragment.this);
              	
              	// parameters
              	updateCfgFromUI();
              	
              	// execute async task
              	task.execute(cfg);
              }catch(Exception e){
              	Log.e(TAG, "Exception", e);
              }
          }
	  }

	  private class NATClickListener implements View.OnClickListener {
		  @Override
          public void onClick(View v) {
              try{
              	final NATDetectTask task = new NATDetectTask(); 	                    	
              	task.setContext(getActivity().getApplicationContext());
              	task.setDialog(null);
              	task.setCallback(iproc);
              	task.setGuiLogger((GuiLogger)getActivity());
              	iproc.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							LOGGER.debug("cancelling NAT detect task");
							task.cancel(false);
						}
					});
              	
              	// parameters
              	updateCfgFromUI();
              	
              	// execute async task
              	iproc.start();
              	task.execute(cfg);
              }catch(Exception e){
              	Log.e(TAG, "Exception", e);
              }
          }
	  }
	  
	  private class TraverseClickListener implements View.OnClickListener {
		  @Override
          public void onClick(View v) {
              try{
              	final TraverseTask task = new TraverseTask(); 	                    	
              	task.setContext(getActivity().getApplicationContext());
              	task.setDialog(null);
              	task.setCallback(iproc);
              	task.setGuiLogger((GuiLogger)getActivity());
              	iproc.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							LOGGER.debug("cancelling NAT detect task");
							task.cancel(false);
						}
					});
              	
              	// parameters
              	updateCfgFromUI();
              	
              	// execute async task
              	iproc.start();
              	task.execute(cfg);
              }catch(Exception e){
              	Log.e(TAG, "Exception", e);
              }
          }
	  }
	  
	  private void startNATtimeoutTask(int type){
		  try{
            	final NATTimeoutTask task = new NATTimeoutTask(); 	                    	
            	task.setContext(getActivity().getApplicationContext());
            	task.setDialog(null);
            	task.setCallback(iproc);
            	task.setGuiLogger((GuiLogger)getActivity());
            	iproc.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							LOGGER.debug("cancelling NAT detect task");
							task.cancel(false);
						}
					});
            	
            	// parameters
            	updateCfgFromUI();
            	
            	// execute async task
            	iproc.start();
            	TaskAppConfigNATTimeout cfgExt = new TaskAppConfigNATTimeout();
            	cfgExt.setCfg(cfg);
            	cfgExt.setTestType(type);
            	task.execute(cfgExt);
            }catch(Exception e){
            	Log.e(TAG, "Exception", e);
            }
	  }
	  
	  public synchronized void removeObserver(MessageObserver ob){
		  if (ob==null) throw new NullPointerException("Observer is null");
		  this.mObservers.remove(ob);
	  }
	  
	  private void startBenchmarkTask(int i) {
		  try{
          	final BenchTask task = new BenchTask();
          	task.setFrag(this);
          	task.setContext(getActivity());
          	task.setDialog(null);
          	task.setCallback(iproc);
          	task.setResources(getActivity().getResources());
          	iproc.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							LOGGER.debug("cancelling benchmark");
							task.cancel(false);
						}
					});
          	
          	// parameters
          	updateCfgFromUI();
          	
          	// execute async task
          	iproc.start();
          	this.onTaskUpdate(new DefaultAsyncProgress(1, "Starting benchmark", "Starting benchmark"), 1);
          	
          	// initialize task parameters as a copy of original ones
          	BenchTaskParam params = new BenchTaskParam();
          	TaskAppConfig newCfg = new TaskAppConfig(cfg);

          	params.setCfg(newCfg);
          	synchronized(this.mObservers){
          		this.mObservers.add(task);
          	}
          	
          	task.execute(params);
          }catch(Exception e){
          	Log.e(TAG, "Exception in BenchmarkTask()", e);
          }
	  }
	  
	  private void startRandomTask(int noLvl){
		  try{
            	final RandomTask task = new RandomTask(); 	                    	
            	task.setContext(getActivity());
            	task.setDialog(null);
            	task.setCallback(iproc);
            	task.setResources(getActivity().getResources());
            	iproc.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							LOGGER.debug("cancelling NAT Random task");
							task.cancel(false);
						}
					});
            	
            	// parameters
            	updateCfgFromUI();
            	
            	// execute async task
            	iproc.start();
            	
            	if (noLvl==1){
            		this.onTaskUpdate(new DefaultAsyncProgress(1, "Starting noRecv scan", 
            				"Starting noRecv scan; TcpDump filter on server side: \ntcpdump -nn -v -S -X -i eth0 'udp and src PUBLIC and not port 5060'"), 1);
            	} else if (noLvl==2){
            		this.onTaskUpdate(new DefaultAsyncProgress(1, "Starting noStun scan", 
            				"Starting noStun scan; TcpDump filter on server side: \ntcpdump -nn -v -S -X -i eth0 'udp and src PUBLIC and not port 5060'"), 1);
            	}
            	
            	Random rnd = new Random(System.currentTimeMillis());
            	RandomTaskParam params = new RandomTaskParam();
            	TaskAppConfig newCfg = new TaskAppConfig(cfg);
            	if (noLvl==2) newCfg.setStunPort(40000 + (rnd.nextInt() % 5000));	 // 2 ~ no STUN
            	
       		// obtain shared preferences
       		SharedPreferences sprefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

       		// Port sleep
       		String portSleepMilliStr = sprefs.getString("port_sleep", "0");
       		int portSleepMilli = 0;
       		try {
       			portSleepMilli = Integer.parseInt(portSleepMilliStr);
       		} catch(NumberFormatException e){
       			LOGGER.warn("Invalid format, should be number", e);
       		}
       		
       		LOGGER.debug("Shared preferences used; port_sleep=" + portSleepMilli);

			// Stun port count 
			int stunPortCount = 99;
       		String stunPortcountStr = sprefs.getString("random_portCount", "99");
       		try {
       			stunPortCount = Integer.parseInt(stunPortcountStr);
       		} catch(NumberFormatException e){
       			LOGGER.warn("Invalid format, should be number", e);
       		}
       		
       		LOGGER.debug("Shared preferences used; stunPortCount=" + stunPortCount);

            	params.setCfg(newCfg);
            	params.setStunPorts(stunPortCount);
            	params.setNoRecv(noLvl > 0);
            	params.setNoStun(noLvl > 1);
            	params.setPause(portSleepMilli);
            	
            	task.execute(params);
            }catch(Exception e){
            	Log.e(TAG, "Exception in RandomTask()", e);
            }
	  }
	  
	private void startSamplingTask() {
		try {
			final RandomTask task = new RandomTask();
			task.setContext(getActivity());
			task.setDialog(null);
			task.setCallback(iproc);
			task.setResources(getActivity().getResources());
			iproc.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					LOGGER.debug("cancelling NAT Sampling task");
					task.cancel(false);
				}
			});

			// parameters
			updateCfgFromUI();

			// execute async task
			iproc.start();

			this.onTaskUpdate(
					new DefaultAsyncProgress(
							1,
							"Starting sampling scan",
							"Starting sampling scan; TcpDump filter on server side: \ntcpdump -nn -v -S -X -i eth0 'udp and src PUBLIC and not port 5060'"),
					1);

			RandomTaskParam params = new RandomTaskParam();
			TaskAppConfig newCfg = new TaskAppConfig(cfg);

			// obtain shared preferences
			SharedPreferences sprefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

			// Port sleep
			String portSleepMilliStr = sprefs.getString("port_sleep", "0");
			int portSleepMilli = 0;
			try {
				portSleepMilli = Integer.parseInt(portSleepMilliStr);
			} catch (NumberFormatException e) {
				LOGGER.warn("Invalid format, should be number", e);
			}

			LOGGER.debug("Shared preferences used; port_sleep="
					+ portSleepMilli);

			// Stun port count
			int stunPortCount = 1;

			params.setFilePrefix("sample");
			params.setCfg(newCfg);
			params.setStunPorts(stunPortCount);
			params.setNoRecv(false);
			params.setNoStun(false);
			params.setPause(portSleepMilli);
			params.setSrcPortStart(1025);
			params.setSrcPortStop(65535);

			task.execute(params);
		} catch (Exception e) {
			Log.e(TAG, "Exception in SamplingTask()", e);
		}
	}

	public void setApi(IServerService api) {
		LOGGER.debug("Setting api from acctivity");
		cfg.setApi(api);
	}

	@Override
	public synchronized void setPublicIP(String IP) {
		if (IP == null)
			return;
		ePublicIP.setText(IP);
	}

	@Override
	public synchronized void setLocalIP(String IP) {
		if (IP==null) return;
		eLocalIP.setText(IP);
	}
	

	@Override
	public void onTaskUpdate(DefaultAsyncProgress progress, int state) {
		final MainFragmentActivity a = (MainFragmentActivity) getActivity();
		a.onTaskUpdate(progress, state);
	}

	@Override
	public void onVisibilityChanged(boolean visible) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1) {
		if (arg1.contentEquals("stun_server_ip")){
			String ip = arg0.getString(arg1, "89.29.122.60");
			this.cfg.setStunServer(ip);
			LOGGER.debug("SharedPreferencesChanged, stun_server_ip=" + ip);
		} else if (arg1.contentEquals("stun_server_port")){
			String port = arg0.getString(arg1, "3478");
			int portInt=3478;
			try {
				portInt = Integer.parseInt(port);
			} catch(NumberFormatException e){
				LOGGER.warn("Invalid number in stun port", e);
			}
			
			this.cfg.setStunPort(portInt);
			LOGGER.debug("SharedPreferencesChanged, stun_server_port=" + portInt);
		} else {
			LOGGER.debug("SharedPreferencesChanged, key=" + arg1);
		}
	}
}

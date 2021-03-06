package org.servalproject.mid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.HandlerThread;
import android.preference.PreferenceManager;

import org.servalproject.mid.networking.Networks;
import org.servalproject.servalchat.BuildConfig;
import org.servalproject.servaldna.ChannelSelector;
import org.servalproject.servaldna.ServalDClient;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.ServalDFailureException;
import org.servalproject.servaldna.ServalDInterfaceException;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by jeremy on 3/05/16.
 */
public class Serval {

	private static final String TAG = "Serval";

	static final int START = 1;
	static final int CPU_LOCK = 2;
	static final int SERVER_UP = 3;

	private Serval(Context context) throws IOException {
		this.context = context;
		this.apkFile = new File(context.getPackageCodePath());
		settings = PreferenceManager.getDefaultSharedPreferences(context);
		File appFolder = context.getFilesDir().getParentFile();
		instancePath = new File(appFolder, "instance");
		uiHandler = new CallbackHandler(context.getMainLooper());

		HandlerThread handlerThread = new HandlerThread("BackgroundHandler");
		handlerThread.start();
		backgroundHandler = new CallbackHandler(handlerThread.getLooper());

		backgroundQueue = new SynchronousQueue<>();
		backgroundThreads = new ThreadPoolExecutor(3, Integer.MAX_VALUE, 5, TimeUnit.SECONDS, backgroundQueue);

		server = new Server(this, context);
		rhizome = new Rhizome(this, context);
		config = new Config();
		networks = new Networks(this);

		selector = new ChannelSelector();
		knownPeers = new KnownPeers(this);
		identities = new Identities(this);

		selfUpdater = SelfUpdater.getSelfUpdater(this);

		// Do the rest of our startup process on a background thread
		backgroundHandler.post(new Runnable() {
			@Override
			public void run() {
				startup();
			}
		});
	}

	private void startup() {
		try {
			ServalDCommand.setInstancePath(instancePath.getPath());
			// if sdcard is available, enable rhizome
			rhizome.updateRhizomeConfig();

			// roll a new restful api password, partly so we only parse config once on the critical path for startup
			// partly for slightly better security
			restfulPassword = new BigInteger(130, new SecureRandom()).toString(32);
			config.set("api.restful.users." + restfulUsername + ".password", restfulPassword);
			config.set("api.restful.newsince_timeout", "3600"); // 1 hour...
			config.set("interfaces.0.match", "eth0,tiwlan0,wlan0,wl0.1,tiap0");
			config.set("interfaces.0.default_route", "on");
			config.set("mdp.enable_inet", "on");
			config.set("log.android.show_pid", "0");
			config.set("log.android.show_time", "0");

			if (!BuildConfig.DEBUG) {
				config.set("log.android.level", "WARN");
				config.set("log.android.dump_config", "0");
				config.set("log.file.dump_config", "0");
			}

			config.sync();

			// TODO if debuggable, set log path to SDcard?
		} catch (ServalDFailureException e) {
			throw new IllegalStateException(e);
		}

		Thread serverThread = new Thread(server, "Servald");
		serverThread.start();
	}

	public final CallbackHandler uiHandler;
	public final CallbackHandler backgroundHandler;
	public final Context context;
	public final File apkFile;
	public final Server server;
	public final Rhizome rhizome;
	public final Config config;
	public final KnownPeers knownPeers;
	public final Identities identities;
	public final SharedPreferences settings;
	private final SelfUpdater selfUpdater;
	private final BlockingQueue<Runnable> backgroundQueue;
	private final ThreadPoolExecutor backgroundThreads;
	private String restfulUsername = "ServalDClient";
	private String restfulPassword;
	private ServalDClient client;
	public final Networks networks;
	public final ChannelSelector selector;
	final File instancePath;

	void onServerStarted() {
		try {
			client = new ServalDClient(server.getHttpPort(), restfulUsername, restfulPassword);
		} catch (ServalDInterfaceException e) {
			throw new IllegalStateException(e);
		}
		networks.onStart();
		identities.onStart();
		knownPeers.onStart();
		rhizome.onStart();
		// TODO trigger other startup here
		server.onStart();
	}

	ServalDClient getResultClient() {
		if (client == null)
			throw new IllegalStateException();
		return client;
	}

	public void runOnThreadPool(Runnable r) {
		backgroundThreads.execute(r);
	}

	public void runOnBackground(Runnable r) {
		backgroundHandler.post(r);
	}

	public void runDelayed(Runnable r, int delay) {
		backgroundHandler.postDelayed(r, delay);
	}

	private static Serval instance;

	public static Serval start(Context appContext) {
		if (instance != null)
			throw new IllegalStateException("instance already created!");
		try {
			instance = new Serval(appContext.getApplicationContext());
			return instance;
		} catch (IOException e) {
			// Yep, we want to crash (this shouldn't happen, but would completely break everything anyway)
			throw new IllegalStateException(e);
		}
	}

	public static Serval getInstance() {
		return instance;
	}

}

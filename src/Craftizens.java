import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.logging.*;

public class Craftizens extends Plugin {
	static final Logger log = Logger.getLogger("Minecraft");
	
	public static boolean DEBUG = false;
	public static String NPC_PREFIX = "\u00C2\u00A7e";
	public static String NPC_SUFFIX = " (NPC)";
	public static String TEXT_COLOR = "\u00C2\u00A7e";
	public static int INTERACT_ITEM = 340;
	public static int INTERACT_ITEM_2 = 340;
	public static int INTERACT_RANGE = 2;
	public static int INTERACT_ANGLE_VARIATION = 25;
	public static boolean INTERACT_ANYTHING = false;
	public static int QADMIN_BOUNDARY_MARKER = 340;
	public static boolean QUESTS_ENABLED = true;
	public static boolean FLATFILE_DATA = false;
    public static String DATA_SOURCE_DRIVER_NAME = "";
    public static String DATA_SOURCE_CONNECTION_URL = "";
    public static String DATA_SOURCE_USERNAME = "";
    public static String DATA_SOURCE_PASSWORD = "";
    public static boolean REPLACE_GROUP = true;
	public static boolean ICONOMY_DETECTED = false;
	public static boolean ALLOW_COMPASS = true;
    public static String NAME = "Craftizens";
	public static String VERSION = "v0.8.2";
	
	public static CraftizenDataSource data;
	public static HashSet<Craftizen> npcs;
	public static HashMap<String,Object> pendingQuests;
	public static HashMap<String,ArrayList<Quest>> activeQuests;
	public static HashMap<String,QuestInfo> newQuests;
	
	private static CraftizenTicker ticker;
	private static CraftizensListener listener;
	
	public void initialize() {
		listener = new CraftizensListener();
		etc.getLoader().addListener(PluginLoader.Hook.COMMAND, listener, this, PluginListener.Priority.MEDIUM);
		etc.getLoader().addListener(PluginLoader.Hook.ARM_SWING, listener, this, PluginListener.Priority.MEDIUM);
		etc.getLoader().addListener(PluginLoader.Hook.BLOCK_DESTROYED, listener, this, PluginListener.Priority.MEDIUM);
		etc.getLoader().addListener(PluginLoader.Hook.BLOCK_CREATED, listener, this, PluginListener.Priority.MEDIUM);
		etc.getLoader().addListener(PluginLoader.Hook.PLAYER_MOVE, listener, this, PluginListener.Priority.MEDIUM);
		etc.getLoader().addListener(PluginLoader.Hook.LOGIN, listener, this, PluginListener.Priority.MEDIUM);
		etc.getLoader().addListener(PluginLoader.Hook.DISCONNECT, listener, this, PluginListener.Priority.MEDIUM);
	}
	
	public void enable() {
		etc.getInstance().addCommand("/craftnpc", "Create an npc");
		etc.getInstance().addCommand("/quest", "Command for working with your quests");
		etc.getInstance().addCommand("/qadmin", "Create, modify, delete, control the quests on your server");
		
		// load properties
		PropertiesFile props = new PropertiesFile("craftizens.properties");
		DEBUG = props.getBoolean("debug-mode", DEBUG);
		NPC_PREFIX = props.getString("npc-name-prefix", NPC_PREFIX);
		NPC_SUFFIX = props.getString("npc-name-suffix", NPC_SUFFIX);
		TEXT_COLOR = props.getString("quest-text-color", TEXT_COLOR);
		INTERACT_ITEM = props.getInt("npc-interact-item", INTERACT_ITEM);
		INTERACT_ITEM_2 = props.getInt("npc-interact-item-2", INTERACT_ITEM_2);
		INTERACT_RANGE = props.getInt("npc-interact-range", INTERACT_RANGE);
		INTERACT_ANGLE_VARIATION = props.getInt("npc-interact-angle-variation", INTERACT_ANGLE_VARIATION);
		INTERACT_ANYTHING = props.getBoolean("interact-anything", INTERACT_ANYTHING);
		QADMIN_BOUNDARY_MARKER = props.getInt("qadmin-boundary-marker", QADMIN_BOUNDARY_MARKER);
		QUESTS_ENABLED = props.getBoolean("quests-enabled", QUESTS_ENABLED);
        FLATFILE_DATA = props.getBoolean("flatfile-data-enabled", FLATFILE_DATA);
		DATA_SOURCE_DRIVER_NAME = props.getString("data-source-driver-name", DATA_SOURCE_DRIVER_NAME);
        DATA_SOURCE_CONNECTION_URL = props.getString("data-source-connection-url", DATA_SOURCE_CONNECTION_URL);
        DATA_SOURCE_USERNAME = props.getString("data-source-username", DATA_SOURCE_USERNAME);
        DATA_SOURCE_PASSWORD = props.getString("data-source-password", DATA_SOURCE_PASSWORD);
        REPLACE_GROUP = props.getBoolean("replace-group", REPLACE_GROUP);
        ALLOW_COMPASS = props.getBoolean("allow-compass", ALLOW_COMPASS);

        if (FLATFILE_DATA)
        	data = new CraftizenFlatfileDataSource();
        else
        	data = new CraftizenSQLDataSource();
	
		loadiConomy();
		
		Craftizen.getPlayerList();		
		npcs = data.loadCraftizens();
		
		ticker = new CraftizenTicker(2000);
		Thread t = new Thread(ticker);
		t.start();
		
		// load quests
		pendingQuests = new HashMap<String,Object>();
		activeQuests = new HashMap<String,ArrayList<Quest>>();
		for (Player p : etc.getServer().getPlayerList()) {
			CraftizensListener.loadActiveQuests(p);
		}
		
		log.info("[" + NAME + "] Plugin " + VERSION + " loaded successfully!");
	}
	
	public void disable() {
		etc.getInstance().removeCommand("/craftnpc");
		etc.getInstance().removeCommand("/quest");
		etc.getInstance().removeCommand("/qadmin");
		
		ticker.stop();
		ticker = null;
		
		for (Craftizen npc : npcs) {
			npc.delete();
		}
		npcs = null;
		
		// save quest progress
		for (String s : activeQuests.keySet()) {
			for (Quest q : activeQuests.get(s)) {
				if (q != null) {
					q.saveProgress();
				}
			}
		}
		
		pendingQuests = null;
		activeQuests = null;
		newQuests = null;
		
		data = null;
	}
	
	public static boolean loadiConomy() {
		if (etc.getLoader().getPlugin("iConomy") != null && etc.getLoader().getPlugin("iConomy").isEnabled()) {
			PropertiesFile iConomySettings = new PropertiesFile(iData.mainDir + "settings.properties");
			if (!iConomySettings.containsKey("use-mysql")) {
				log.warning("[" + NAME + "] iConomy settings failed to be read.");
				ICONOMY_DETECTED = false;
				return false;
			}
			boolean mysql = iConomySettings.getBoolean("use-mysql", false);
			
			// MySQL
			String driver = iConomySettings.getString("driver", "com.mysql.jdbc.Driver");
			String user = iConomySettings.getString("user", "root");
			String pass = iConomySettings.getString("pass", "root");
			String db = iConomySettings.getString("db", "jdbc:mysql://localhost:3306/minecraft");

			// Data
			iData.setup(mysql, 0, driver, user, pass, db);
			log.info("[" + NAME + "] iConomy loaded successfully.");
			ICONOMY_DETECTED = true;
			return true;
		} else {
			log.warning("[" + NAME + "] iConomy failed to load.");
			ICONOMY_DETECTED = false;
			return false;
		}
	}
}

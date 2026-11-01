package dev.mietze.piimager.workbench

internal enum class WbFieldType { TEXT, INTEGER, SWITCH, CHOICE }
internal data class WbField(val key:String,val label:String,val default:String="",val type:WbFieldType=WbFieldType.TEXT,
    val min:Long=0,val max:Long=65535,val options:List<String> = emptyList(),val help:String="",val exportKey:String?=null)
internal data class WbKind(val id:String,val title:String,val category:String,val description:String,
    val fields:List<WbField> = emptyList(),val website:String="",val exportMode:String="Plan declaration")
internal fun field(k:String,l:String,d:String="",help:String="",export:String?=null)=WbField(k,l,d,help=help,exportKey=export)
internal fun integer(k:String,l:String,d:Int,min:Int=0,max:Int=65535,help:String="",export:String?=null)=WbField(k,l,d.toString(),WbFieldType.INTEGER,min.toLong(),max.toLong(),help=help,exportKey=export)
internal fun toggle(k:String,l:String,d:Boolean=false,help:String="",export:String?=null)=WbField(k,l,d.toString(),WbFieldType.SWITCH,help=help,exportKey=export)
internal fun choice(k:String,l:String,d:String,opts:String,help:String="",export:String?=null)=WbField(k,l,d,WbFieldType.CHOICE,options=opts.split('|'),help=help,exportKey=export)
internal object WbCatalog {
    private val reserve=listOf(integer("reserveMiB","Reserved storage (MiB)",0,0,8388608,"A planned reservation, not physical partitioning. Zero consumes no reserved capacity."))
    private val artifact=listOf(field("sourceUrl","Official / image URL",help="HTTPS reference only. Adding a block does not download it."),field("sha256","Expected SHA-256",help="Optional 64 hexadecimal characters."))
    private val boot=listOf(field("path","Boot file path",help="Relative path in the boot volume."),choice("slot","Boot slot","A","A|B|recovery"))+artifact
    private val driver=listOf(field("module","Kernel module or DT overlay"),field("kernel","Target kernel release"),field("parameters","Parameters"),toggle("required","Required at boot",true))+artifact
    private val service=listOf(toggle("autostart","Start with selected OS",true),choice("restart","Restart policy","on-failure","no|on-failure|always"),integer("memoryMiB","RAM budget (MiB)",128,0,1048576),field("serviceUnit","Existing systemd unit",help="Planning reference. No service is executed by this editor."))
    private val image=listOf(integer("reserveMiB","Root/image allocation (MiB)",16384,0,8388608),field("version","Image version","manual"),choice("architecture","Image architecture","arm64","arm64|arm32|x86_64"))+artifact
    private val mcCommon=listOf(
        field("version","Server release","manual","Select a release matching your clients; this is not a latest-version promise."),
        integer("maxPlayers","Maximum players",10,1,10000,export="max-players"),
        field("world","World folder","world","Use a simple folder name, not a path.",export="level-name"),
        choice("mode","Game mode","survival","survival|creative|adventure",export="gamemode"),
        choice("difficulty","Difficulty","normal","peaceful|easy|normal|hard",export="difficulty"),
        toggle("online","Online authentication",true,export="online-mode"),
        field("seed","World seed","",export="level-seed"),
        integer("idle","Idle timeout (minutes)",30,0,1440,export="player-idle-timeout"),
        integer("reserveMiB","World/data reservation (MiB)",1024,0,8388608),
        toggle("autostart","Generate optional user service",false,"Only produces a unit file. Nothing is enabled on the Pi."),
        choice("restart","Restart policy","on-failure","no|on-failure|always"),
        toggle("backup","Include backup helper",true),
        field("sourceUrl","Provider download reference","",help="Open provider to obtain server binaries. They are not bundled."),
        field("sha256","Expected artifact SHA-256"))
    private val java=listOf(integer("port","TCP server port",25565,1,65535,export="server-port"),
        field("motd","Server description","Raspberry Minecraft",export="motd"),
        integer("javaMajor","Required Java major",21,8,50,"Match the selected server release; adjust for newer releases."),
        integer("memoryMiB","Maximum Java heap (MiB)",2048,128,1048576),integer("minHeapMiB","Initial Java heap (MiB)",512,64,1048576),
        integer("viewDistance","View distance (chunks)",8,2,32,export="view-distance"),integer("simulationDistance","Simulation distance",6,2,32,export="simulation-distance"),
        toggle("pvp","Player versus player",true,export="pvp"),toggle("allowList","Allow-list only",false,export="white-list"),
        integer("spawnProtection","Spawn protection",16,0,256,export="spawn-protection"),toggle("hardcore","Hardcore mode",false,export="hardcore"),
        toggle("nether","Enable Nether",true,export="allow-nether"),toggle("commandBlocks","Command blocks",false,export="enable-command-block"),
        toggle("eula","I have read and accept the Minecraft EULA",false,"Only you can accept this. Exported Java startup requires explicit acceptance."))
    private val nukkit=listOf(integer("port","UDP server port",19132,1,65535,export="server-port"),
        field("motd","Server description","Raspberry Nukkit",export="motd"),
        integer("javaMajor","Required Java major",21,8,50,"Select the Java major required by your Nukkit build."),
        integer("memoryMiB","Maximum Java heap (MiB)",1536,128,1048576),integer("minHeapMiB","Initial Java heap (MiB)",512,64,1048576),
        integer("viewDistance","View distance (chunks)",8,3,32,export="view-distance"),
        toggle("allowList","Allow-list only",false,export="white-list"),toggle("pvp","Player versus player",true,export="pvp"),
        choice("implementation","Nukkit distribution","Cloudburst Nukkit","Cloudburst Nukkit|Legacy Nukkit","nukkit.org now describes legacy Nukkit and its successor. This remains a Nukkit JAR block, not Endstone."),
        field("plugins","Plugin notes",help="Plugin compatibility depends on the chosen Nukkit build."))
    private val bedrock=listOf(integer("port","IPv4 UDP port",19132,1,65535,export="server-port"),
        integer("portV6","IPv6 UDP port",19133,1,65535,export="server-portv6"),
        field("serverName","Server name","Raspberry Bedrock",export="server-name"),
        toggle("allowList","Allow-list only",false,export="allow-list"),toggle("cheats","Allow cheats",false,export="allow-cheats"),
        toggle("forceMode","Force game mode",false,export="force-gamemode"),
        toggle("lanVisibility","LAN visibility",false,"When on, the server also binds discovery ports 19132/19133.",export="enable-lan-visibility"),
        integer("viewDistance","View distance",8,5,96,export="view-distance"),integer("tickDistance","Tick distance",4,4,12,export="tick-distance"),
        integer("threads","Maximum threads",4,0,128,export="max-threads"),integer("memoryMiB","RAM planning budget (MiB)",2048,128,1048576),
        choice("permission","Default player permission","member","visitor|member|operator",export="default-player-permission-level"),
        toggle("textures","Require texture pack",false,export="texturepack-required"),toggle("contentLog","Content log",false,export="content-log-file-enabled"),
        integer("compressionThreshold","Compression threshold",1,0,65535,export="compression-threshold"),
        choice("compression","Compression","zlib","zlib|snappy",export="compression-algorithm"),
        toggle("watchdog","Script watchdog",true,export="script-watchdog-enable"),
        toggle("strictMovement","Strict authoritative movement",false,export="server-authoritative-movement-strict"),
        toggle("strictDismount","Strict dismount",false,export="server-authoritative-dismount-strict"),
        toggle("strictInteractions","Strict interactions",false,export="server-authoritative-entity-interactions-strict"),
        choice("execution","Execution target","native x86_64","native x86_64|external x86_64 host|ARM translation planned","Official Bedrock binaries are not native Raspberry Pi ARM binaries. A planned translation layer is not installed or verified by this app."))
    private val modClient=listOf(
        field("minecraftVersion","Minecraft version","manual"),field("loaderVersion","Loader version","manual"),integer("javaMajor","Required Java major",21,8,50),
        integer("memoryMiB","Client heap budget (MiB)",2048,256,1048576),field("profile","Profile name","Raspberry mod client"),
        field("sourceUrl","Official loader reference","",help="HTTPS reference only. No installer is run by adding this block."),field("sha256","Expected installer SHA-256"))
    val kinds:List<WbKind> = buildList {
        add(WbKind("minecraft","Minecraft Server","Minecraft","Choose Java or Bedrock, then a server implementation."))
        add(WbKind("mc.java","Java","Minecraft","Vanilla Minecraft Java server configuration and launch bundle.",mcCommon+java,"https://www.minecraft.net/en-us/download/server","Config + launcher"))
        add(WbKind("mc.bukkit","Bukkit","Minecraft","Bukkit / CraftBukkit-style Java server plan. Obtain and verify the server JAR separately.",mcCommon+java+choice("software","Bukkit profile","CraftBukkit","CraftBukkit|Bukkit-compatible"),"https://bukkit.org/","Config + launcher"))
        add(WbKind("mc.nukkit","Nukkit","Minecraft","Java-based Bedrock server. Nukkit, not Java Edition.",mcCommon+nukkit,"https://nukkit.org/","Config + launcher"))
        add(WbKind("mc.bedrock","Bedrock","Minecraft","Official Bedrock Dedicated Server. x86_64 compatibility is checked.",mcCommon+bedrock,"https://www.minecraft.net/en-us/download/server/bedrock","Config + launcher"))
        add(WbKind("mc.mod.forge","Mod Client · Forge","Minecraft","Client-side Forge profile declaration for a Raspberry/Linux target. No launcher or mod is installed automatically.",modClient,"https://files.minecraftforge.net/","Client plan"))
        add(WbKind("mc.mod.fabric","Mod Client · Fabric","Minecraft","Client-side Fabric profile declaration for a Raspberry/Linux target. No launcher or mod is installed automatically.",modClient,"https://fabricmc.net/","Client plan"))
        listOf("os.pi64" to "Raspberry Pi OS", "os.ubuntu" to "Ubuntu", "os.dietpi" to "DietPi", "os.libreelec" to "LibreELEC", "os.homeassistant" to "Home Assistant OS", "os.kali" to "Kali Linux", "os.android" to "Android", "os.custom" to "Custom OS image").forEach{(id,title)->add(WbKind(id,title,"Operating systems","OS/image declaration. Bootability requires an actual compatible image.",image))}
        listOf("boot.a" to "Boot A","boot.b" to "Boot B","boot.kernel" to "Kernel","boot.initramfs" to "Initramfs","boot.firmware" to "Firmware","boot.file" to "Custom boot file","boot.patch" to "Boot acceptable patch file").forEach{(id,title)->add(WbKind(id,title,"Boot", "Boot component reference; no EEPROM or disk changes are performed.",boot))}
        add(WbKind("boot.selector","MultiBoot selector","Boot","Plan mutually exclusive OS slots; not a physical MultiBoot patcher.",listOf(integer("timeout","Selection timeout (s)",5,0,300),choice("defaultSlot","Default slot","A","A|B|recovery"),field("implementation","Boot selector implementation"))+artifact))
        add(WbKind("boot.config","config.txt fragment","Boot","Export a reviewable boot configuration fragment.",listOf(field("key","Setting key","dtoverlay"),field("value","Setting value",""),choice("section","Board section","all","all|pi5|pi4|pi3|pi02")),exportMode="Config fragment"))
        add(WbKind("boot.cmdline","cmdline token","Boot","Single reviewable kernel-command-line token.",listOf(field("key","Parameter","quiet"),field("value","Value","")),exportMode="Config fragment"))
        listOf("driver.usb" to "USB driver","driver.wifi" to "Wi-Fi driver","driver.bluetooth" to "Bluetooth driver","driver.audio" to "Audio driver","driver.display" to "Display driver","driver.storage" to "Storage driver","driver.gpu" to "GPU driver","driver.sensor" to "Sensor driver","driver.overlay" to "DTB overlay","driver.gpio" to "GPIO / HAT").forEach{(id,title)->add(WbKind(id,title,"Drivers","Attach or reference a compatible driver; kernel/board matching still needs testing.",driver))}
        listOf("storage.fat32" to "FAT32 boot volume","storage.ext4" to "ext4 root/data volume","storage.swap" to "Swap reservation","storage.volume" to "Service data volume","storage.free" to "Reserved free space","storage.recovery" to "Recovery image (optional)").forEach{(id,title)->add(WbKind(id,title,"Storage",if(id=="storage.free")"Reserved space is excluded from the unallocated vacuum." else "Allocation plan only. No physical partition is created.",listOf(integer("reserveMiB","Size (MiB)",if(id=="storage.fat32")512 else 1024,0,8388608),field("label","Volume label"),field("mount","Mount point"))+artifact))}
        add(WbKind("net.ethernet","Ethernet","Network","Link / addressing declaration.",listOf(field("interface","Interface","eth0"),choice("mode","Addressing","dhcp","dhcp|static"),field("address","Address / CIDR"),field("gateway","Gateway"),integer("mtu","MTU",1500,576,9216))))
        add(WbKind("net.wifi","Wi-Fi","Network","Wi-Fi planning. Passwords are deliberately not stored in project exports.",listOf(field("ssid","SSID"),field("country","Country code","DE"),choice("band","Band","auto","auto|2.4GHz|5GHz"),toggle("hidden","Hidden SSID"),choice("mode","Addressing","dhcp","dhcp|static"))))
        add(WbKind("net.dns","DNS","Network","Resolver plan.",listOf(field("primary","Primary resolver"),field("secondary","Secondary resolver"),field("search","Search domain"))))
        add(WbKind("net.firewall","Firewall rule","Network","Review-only firewall declaration; no port is opened by adding it.",listOf(integer("port","Port",25565,1,65535),choice("protocol","Protocol","tcp","tcp|udp"),choice("policy","Policy","allow","allow|deny"),field("source","Source CIDR",""))))
        add(WbKind("net.vpn","VPN","Network","VPN configuration reference; secrets are not embedded.",listOf(choice("provider","Type","WireGuard","WireGuard|OpenVPN|other"),field("profile","Profile reference"),toggle("required","Required connection",true))))
        add(WbKind("net.proxy","Proxy","Network","Proxy routing declaration.",listOf(field("host","Host"),integer("port","Port",8080,1,65535),choice("protocol","Protocol","http","http|https|socks5"))))
        add(WbKind("net.access","Access rule","Network","Human-readable access policy.",listOf(field("subject","User / network"),field("resource","Resource"),choice("policy","Policy","allow","allow|deny|read-only"))))
        add(WbKind("runtime.java","Java runtime","Runtimes","Match the runtime major to the selected server JAR.",listOf(integer("major","Java major",21,8,50),choice("distribution","Distribution","OpenJDK","OpenJDK|Temurin|other"))))
        listOf("runtime.docker" to "Docker host","runtime.python" to "Python runtime","runtime.node" to "Node.js runtime").forEach{(id,title)->add(WbKind(id,title,"Runtimes","Runtime declaration with explicit version.",listOf(field("version","Required version","manual"),field("architecture","Required architecture","arm64"))+service))}
        listOf("service.ssh" to "SSH","service.vnc" to "VNC / desktop access","service.monitor" to "Monitoring","service.backup" to "Backup service","service.watchdog" to "Watchdog","service.mount" to "Automatic mount","service.update" to "Update service","service.init" to "Init service","service.remote" to "Remote access","server.web" to "Web server","server.database" to "Database","server.files" to "File server","server.media" to "Media server","server.game" to "Custom game server","service.custom" to "Custom service").forEach{(id,title)->add(WbKind(id,title,"Services", "Configure and connect a service declaration in the graph.",service+listOf(field("version","Version"),integer("port","Optional listening port",0,0,65535),choice("protocol","Protocol","tcp","tcp|udp"))+reserve))}
        add(WbKind("note","Workspace note","Notes","A freely placed annotation. It never starts or allocates storage.",listOf(field("text","Text","Your note")) ))
    }
    private val byId by lazy{kinds.associateBy{it.id}}
    fun contains(id:String):Boolean=byId.containsKey(id)
    fun kind(id:String):WbKind=byId[id]?:error("Unknown block type $id")
    fun create(id:String,x:Float=48f,y:Float=48f):WbNode {val k=kind(id);return WbNode(type=id,name=k.title,x=x,y=y,params=k.fields.associate{it.key to it.default})}
    fun fieldErrors(n:WbNode):List<String> = kind(n.type).fields.mapNotNull{f->val v=n.value(f.key)
        when(f.type){
            WbFieldType.INTEGER->if(v.toLongOrNull()?.let{it in f.min..f.max}!=true)"${f.label}: enter ${f.min}–${f.max}." else null
            WbFieldType.SWITCH->if(v !in listOf("true","false"))"${f.label}: invalid switch." else null
            WbFieldType.CHOICE->if(v !in f.options)"${f.label}: invalid choice." else null
            WbFieldType.TEXT->when{v.length>2048||'\u0000' in v||'\n' in v||'\r' in v->"${f.label}: use one line, at most 2048 characters."
                f.key=="sourceUrl"&&v.isNotBlank()&&!WbCodec.safeHttps(v)->"Use an HTTPS source without credentials."
                f.key=="sha256"&&v.isNotBlank()&&!Regex("[0-9a-fA-F]{64}").matches(v)->"SHA-256 requires 64 hexadecimal characters."
                f.key=="world"&&!Regex("[A-Za-z0-9 _.-]{1,80}").matches(v)->"World folder uses letters, digits, spaces, _ . - only."
                f.key=="world"&&(v=="."||v==".."||v.endsWith("."))->"Invalid world folder."
                f.key=="serverName"&&';' in v->"Server name must not contain a semicolon."
                else->null}
        }
    }
}

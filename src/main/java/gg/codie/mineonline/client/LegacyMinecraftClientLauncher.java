package gg.codie.mineonline.client;

import gg.codie.minecraft.client.Options;
import gg.codie.minecraft.client.VersionFile;
import gg.codie.mineonline.*;
import gg.codie.mineonline.gui.MenuManager;
import gg.codie.mineonline.gui.rendering.DisplayManager;
import gg.codie.mineonline.gui.rendering.Renderer;
import gg.codie.mineonline.lwjgl.OnCreateListener;
import gg.codie.mineonline.lwjgl.OnUpdateListener;
import gg.codie.mineonline.patches.*;
import gg.codie.mineonline.patches.lwjgl.LWJGLDisplayPatch;
import gg.codie.mineonline.patches.lwjgl.LWJGLOrthoPatch;
import gg.codie.mineonline.patches.lwjgl.LWJGLPerspectivePatch;
import gg.codie.mineonline.patches.minecraft.GuiScreenPatch;
import gg.codie.mineonline.patches.minecraft.ScaledResolutionConstructorPatch;
import gg.codie.mineonline.utils.JREUtils;
import gg.codie.utils.FileUtils;
import gg.codie.utils.OSUtils;
import gg.codie.utils.TransferableImage;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.applet.Applet;
import java.applet.AppletStub;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;

public class LegacyMinecraftClientLauncher extends Applet implements AppletStub{
    Applet minecraftApplet;

    String jarPath;
    String serverAddress;
    String serverPort;
    String MPPass;

    Renderer renderer;

    MinecraftVersion minecraftVersion;

    URLClassLoader classLoader;

    public LegacyMinecraftClientLauncher(String jarPath, String serverAddress, String serverPort, String MPPass) throws Exception {
        this.jarPath = jarPath;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.MPPass = MPPass;

        if(serverAddress != null && serverPort == null)
            this.serverPort = "25565";

        minecraftVersion = MinecraftVersionRepository.getSingleton().getVersion(jarPath);

        this.classLoader = new URLClassLoader(new URL[] { Paths.get(jarPath).toUri().toURL() });
    }

    boolean firstUpdate = true;
    public void startMinecraft() throws Exception {

        System.gc();

        if(serverAddress != null) {
            try {
                new Options(LauncherFiles.MINECRAFT_OPTIONS_PATH).setOption("lastServer", serverAddress + "_" + serverPort);
            } catch (Exception ex) {

            }
        }

        fullscreen = Settings.settings.has(Settings.FULLSCREEN) && Settings.settings.getBoolean(Settings.FULLSCREEN);

        String CP = "-cp";

        String classpath = System.getProperty("java.class.path").replace("\"", "");

        try {
            Class rubyDungClass;
            try {
                rubyDungClass = classLoader.loadClass("com.mojang.rubydung.RubyDung");
            } catch (ClassNotFoundException ex) {
                rubyDungClass = classLoader.loadClass("com.mojang.minecraft.RubyDung");
            }

            // TODO: Launch RubyDung in frame.
//            Method mainFunction = rubyDungClass.getDeclaredMethod("main", String[].class);
//            String[] params = null;
//
//            DisplayManager.closeDisplay();
//
//            Display.setCreateListener(new OnCreateListener() {
//                @Override
//                public void onCreateEvent() {
//                    renderer = new Renderer();
//                    try {
//                        Display.setParent(DisplayManager.getCanvas());
//                        Display.setDisplayMode(new DisplayMode(DisplayManager.getFrame().getWidth(), DisplayManager.getFrame().getHeight()));
//                    } catch (Exception ex) {
//
//                    }
//                }
//            });
//
//            mainFunction.invoke(null, (Object)params);
            LinkedList<String> arguments = new LinkedList<>();
            arguments.add(JREUtils.getJavaExecutable());
            arguments.add("-Djava.library.path=" + LauncherFiles.MINEONLINE_NATIVES_FOLDER);

            if (Settings.settings.has(Settings.CLIENT_LAUNCH_ARGS) && !Settings.settings.getString(Settings.CLIENT_LAUNCH_ARGS).isEmpty())
                arguments.addAll(Arrays.asList(Settings.settings.getString(Settings.CLIENT_LAUNCH_ARGS).split(" ")));

            arguments.add(CP);
            arguments.add(classpath + LibraryManager.getClasspathSeparator() + LauncherFiles.LWJGL_JAR + LibraryManager.getClasspathSeparator() + LauncherFiles.LWJGL_UTIL_JAR + LibraryManager.getClasspathSeparator() + jarPath);
            arguments.add(rubyDungClass.getCanonicalName());

            System.out.println("Launching RubyDung!  " + String.join(" ", arguments));


            java.util.Properties props = System.getProperties();
            ProcessBuilder processBuilder = new ProcessBuilder(arguments);
            Map<String, String> env = processBuilder.environment();
            for(String prop : props.stringPropertyNames()) {
                env.put(prop, props.getProperty(prop));
            }
            processBuilder.directory(new File(System.getProperty("user.dir")));

            processBuilder.start();
            DisplayManager.closeDisplay();
            System.exit(0);
            return;
        } catch (Exception ex) {
           // ex.printStackTrace();
        }


        if (minecraftVersion != null && minecraftVersion.type.equals("launcher")) {
            DisplayManager.closeDisplay();
            if(DisplayManager.getFrame() != null)
                DisplayManager.getFrame().dispose();

            Settings.loadSettings();
            String updateURLString = Settings.settings.has(Settings.MINECRAFT_UPDATE_URL) ? Settings.settings.getString(Settings.MINECRAFT_UPDATE_URL) : null;

            SystemSetPropertyPatch.banNativeChanges();

            if(updateURLString != null && !updateURLString.isEmpty()) {
                URL updateURL = new URL(updateURLString);
                File currentJar = new File(LauncherFiles.MINECRAFT_BINARIES_PATH + FileUtils.getFileName(updateURL));
                if(currentJar.exists()) {
                    int existingJarSize = (int)currentJar.length();
                    HttpURLConnection versionRequest = (HttpURLConnection) new URL(updateURLString).openConnection();
                    versionRequest.setRequestMethod("HEAD");
                    versionRequest.connect();

                    if(existingJarSize != versionRequest.getContentLength()) {
                        VersionFile.delete();
                    }
                }
            }

            SocketPatch.watchSockets();
            URLPatch.redefineURL(updateURLString);

            try {
                Class launcherClass = classLoader.loadClass("net.minecraft.LauncherFrame");
                Method mainFunction = launcherClass.getDeclaredMethod("main", String[].class);
                mainFunction.invoke(null, new Object[] { new String[0]} );
            } catch (ClassNotFoundException ex) {
                JOptionPane.showMessageDialog(null, "Failed to launch minecraft.");
            }

            return;
        }

        if (OSUtils.isMac() && minecraftVersion.forceFullscreenMacos) {
            Display.setDisplayMode(Display.getDesktopDisplayMode());
            Display.setFullscreen(true);
            DisplayManager.fullscreen(true);
            fullscreen = true;

            appletResize(DisplayManager.getFrame().getWidth(), DisplayManager.getFrame().getHeight());
        } else if (fullscreen) {
            if (minecraftVersion != null && minecraftVersion.enableFullscreenPatch) {
                setFullscreen(true);
            } else {
                Display.setDisplayMode(Display.getDesktopDisplayMode());
                Display.setFullscreen(true);
            }
        }

        String appletClassName = MinecraftVersion.getAppletClass(jarPath);

        Frame frame = DisplayManager.getFrame();

        LWJGLDisplayPatch.createListener = new OnCreateListener() {
            @Override
            public void onCreateEvent() {
                renderer = new Renderer();
            }
        };

        Class appletClass;

        try {
            appletClass = classLoader.loadClass(appletClassName);
        } catch (Exception ex) {
            ex.printStackTrace();
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(null, "This version is currently unsupported.");
                }
            });
            return;
        }

        Field minecraftField = null;

        try {
            minecraftField = appletClass.getDeclaredField("minecraft");
        } catch (NoSuchFieldException ne) {
            for (Field field : appletClass.getDeclaredFields()) {
                if (field.getType().getPackage() == appletClass.getPackage()) {
                    minecraftField = field;
                    continue;
                }
            }
        }

        Class minecraftClass = null;

        if (minecraftField != null)
            minecraftClass = minecraftField.getType();

        Runnable minecraftImpl = null;

        if (minecraftClass != null && !(minecraftVersion != null && !minecraftVersion.useMinecraftImpl)) {
            try {
                File jarFile = new File(jarPath);

                if (!jarFile.exists() || jarFile.isDirectory())
                    return;

                Constructor constructor = null;

                if(minecraftVersion != null && minecraftVersion.minecraftImplClass != null) {
                    // If a class name was provided in the version manifest, use it.
                    try {
                        Class clazz = classLoader.loadClass(minecraftVersion.minecraftImplClass);
                        constructor = clazz.getDeclaredConstructor(
                                Component.class, Canvas.class, appletClass, int.class, int.class, boolean.class, Frame.class
                        );
                        minecraftImpl = (Runnable) constructor.newInstance(null, DisplayManager.getCanvas(), null, Display.getWidth(), Display.getHeight(), fullscreen, frame);
                    } catch (Throwable e) {

                    }
                } else {
                    // If the jar isn't obfuscated (debug) find the class by name.
                    try {
                        Class clazz = classLoader.loadClass("net.minecraft.src.MinecraftImpl");
                        constructor = clazz.getDeclaredConstructor(
                                Component.class, Canvas.class, appletClass, int.class, int.class, boolean.class, Frame.class
                        );
                    } catch (Throwable e) {

                    }

                    if (constructor == null) {
                        java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile.getPath());
                        java.util.Enumeration enumEntries = jar.entries();
                        while (enumEntries.hasMoreElements()) {
                            java.util.jar.JarEntry file = (java.util.jar.JarEntry) enumEntries.nextElement();
                            String fileName = file.getName();
                            if (file.isDirectory() || !fileName.endsWith(".class")) {
                                continue;
                            }

                            if (fileName.contains("/"))
                                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);

                            if (constructor == null) {
                                // Ideally, we'd check if the class extends Minecraft
                                // But due to obfuscation we have to settle for this.
                                try {
                                    if (fileName.equals("Minecraft.class")) {
                                        continue;
                                    }

                                    Class clazz = classLoader.loadClass(file.getName().replace(".class", "").replace("/", "."));
                                    constructor = clazz.getDeclaredConstructor(
                                            Component.class, Canvas.class, appletClass, int.class, int.class, boolean.class, Frame.class
                                    );
                                } catch (Throwable e) {
                                }
                            }

                            if (constructor != null) {
                                System.out.println("found MinecraftImpl: " + file.getName());

                                try {
                                    minecraftImpl = (Runnable) constructor.newInstance(null, DisplayManager.getCanvas(), null, Display.getWidth(), Display.getHeight(), fullscreen, frame);
                                } catch(InvocationTargetException e) {
                                    constructor = null;
                                    minecraftImpl = null;
                                }
                            }

                            // If we're manually creating MinecraftImpl there's a couple of other things to do:
                            if (minecraftImpl != null) {
                                // Setup the minecraft session.
                                for (Field field : minecraftClass.getDeclaredFields()) {
                                    try {
                                        constructor = field.getType().getConstructor(
                                                String.class, String.class
                                        );
                                        if (constructor == null) {
                                            continue;
                                        }
                                        field.set(minecraftImpl, constructor.newInstance(Session.session.getUsername(), Session.session.getSessionToken()));
                                        break;
                                    } catch (Exception e) {
                                        continue;
                                    }
                                }
                                // Join a server (if provided)
                                if (serverAddress != null && serverPort != null) {
                                    for (Method method : minecraftClass.getMethods()) {
                                        try {
                                            if (Arrays.equals(method.getParameterTypes(), (new Object[]{String.class, int.class}))) {
                                                method.invoke(minecraftImpl, serverAddress, Integer.parseInt(serverPort));
                                                break;
                                            }
                                        } catch (Exception e) {
                                            continue;
                                        }
                                    }
                                }
                                break;
                            }

                        }

                        jar.close();
                    }
                }
            } catch(Exception e){
                e.printStackTrace();
            }
        }


        if (minecraftImpl == null) {
            try {
                minecraftApplet = (Applet) appletClass.newInstance();
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }

            //Here we set the applet's stub to a custom stub, rather than letting it use the default.
            //And also we now set the width and height of the applet on the screen.
            minecraftApplet.setStub(this);
            minecraftApplet.setPreferredSize(new Dimension(Display.getWidth(), Display.getHeight()));

            //This puts the applet into a window so that it can be shown on the screen.
            frame.add(minecraftApplet);
            frame.pack();

            DisplayManager.getCanvas().setVisible(false);
        }

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                closeApplet();
            }
        });
        frame.addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                appletResize(frame.getWidth(), frame.getHeight());
            }

            @Override
            public void componentMoved(ComponentEvent e) {
            }

            @Override
            public void componentShown(ComponentEvent e) {
            }

            @Override
            public void componentHidden(ComponentEvent e) {
            }
        });

        frame.setBackground(Color.black);

        MenuManager.formopen = false;

        LWJGLDisplayPatch.updateListener = new OnUpdateListener() {
            @Override
            public void onUpdateEvent() {
                if (renderer != null) {
                    if (Globals.DEV) {
                        //renderer.renderStringIngame(new Vector2f(1, 1), 8, "MineOnline Dev " + Globals.LAUNCHER_VERSION, org.newdawn.slick.Color.white);
                    }

                    // Make sure classic is behaving
                    if (minecraftVersion.guiClass != null)
                        appletResize(DisplayManager.getFrame().getWidth(), DisplayManager.getFrame().getHeight());

                    if (minecraftVersion != null) {
                        if (minecraftVersion.enableScreenshotPatch) {
                            try {
                                float opacityMultiplier = System.currentTimeMillis() - lastScreenshotTime;
                                if (opacityMultiplier > 5000) {
                                    opacityMultiplier -= 5000;
                                    opacityMultiplier = -(opacityMultiplier / 500);
                                    opacityMultiplier += 1;
                                } else {
                                    opacityMultiplier = 1;
                                }

                                if (opacityMultiplier > 0) {
                                    renderer.renderStringIngame(new Vector2f(2, 190), 8, "Saved screenshot as " + lastScreenshotName, new org.newdawn.slick.Color(1, 1, 1, 1 * opacityMultiplier));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        if (Keyboard.getEventKey() == Keyboard.KEY_F2 && !Keyboard.isRepeatEvent() && Keyboard.getEventKeyState() && !f2wasDown) {
                            screenshot();
                            f2wasDown = true;
                        }
                        if (Keyboard.getEventKey() == Keyboard.KEY_F2 && !Keyboard.isRepeatEvent() && !Keyboard.getEventKeyState()) {
                            f2wasDown = false;
                        }
                    }
                    if (minecraftVersion != null && minecraftVersion.enableFullscreenPatch && !(OSUtils.isMac() && minecraftVersion.forceFullscreenMacos)) {
                        if (Keyboard.getEventKey() == Keyboard.KEY_F11 && !Keyboard.isRepeatEvent() && Keyboard.getEventKeyState() && !f11WasDown) {
                            setFullscreen(!fullscreen);
                            f11WasDown = true;
                        }
                        if (Keyboard.getEventKey() == Keyboard.KEY_F11 && !Keyboard.isRepeatEvent() && !Keyboard.getEventKeyState()) {
                            f11WasDown = false;
                        }
                    }
                }

                if (firstUpdate) {
                    firstUpdate = false;
                }

                // This stops the mouse from spinning out on mac os.
                if (OSUtils.isMac() && minecraftVersion.enableMacosCursorPatch) {
                    try {
                        // If you're not in a menu...
                        if (Mouse.getNativeCursor() != null) {
                            Mouse.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        };

        DisplayManager.getFrame().setTitle("Minecraft");
        DisplayManager.closeDisplay();

        // Patches
        SocketPatch.watchSockets();
        URLPatch.redefineURL(null);

        if (minecraftVersion != null && minecraftVersion.useFOVPatch)
            LWJGLPerspectivePatch.useCustomFOV();

        if (Settings.settings.optInt(Settings.GUI_SCALE, 0) != 0 && minecraftVersion != null) {
            if (minecraftVersion.scaledResolutionClass != null) {
                ScaledResolutionConstructorPatch.useGUIScale(minecraftVersion.scaledResolutionClass, classLoader);
                LWJGLOrthoPatch.useGuiScale();
            } else if (minecraftVersion.guiClass != null && minecraftVersion.guiScreenClass != null) {
                GuiScreenPatch.useGUIScale(minecraftVersion.guiScreenClass, classLoader);
                LWJGLOrthoPatch.useGuiScale();
            }
        }
        // Allows c0.0.15a to connect to servers.
        InetSocketAddressPatch.allowCustomServers(serverAddress, serverPort);
        // Allows c0.0.15a to have a username sent to servers.
        if (minecraftVersion != null && minecraftVersion.baseVersion.equals("c0.0.15a"))
            ByteBufferPatch.enableC0015aUsernames(Session.session.getUsername());

        try {
            Options options = new Options(LauncherFiles.MINECRAFT_OPTIONS_PATH);
            options.setOption("guiScale", "" + Settings.settings.optInt(Settings.GUI_SCALE, 0));
        } catch (Exception ex) {
            System.err.println("Couldn't update options.txt");
        }

        if (minecraftImpl != null) {
            minecraftImpl.run();
        } else {
            minecraftApplet.init();
            minecraftApplet.start();
        }
    }

    boolean f2wasDown = false;
    boolean f11WasDown = false;

    void closeApplet(){
        if(minecraftApplet != null) {
            minecraftApplet.stop();
            minecraftApplet.destroy();
        }
        try {
            DisplayManager.closeDisplay();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        DisplayManager.getFrame().dispose();
        System.exit(0);
    }


    URL StringToURL(String URLString){
        try{
            return new URL(URLString);
        }catch (Exception ex){
            ex.printStackTrace();
            return null;
        }
    }



    // HACKY RESIZING, needs testing.
    /*
        Minecraft applets never had any resizing code, so to implement it I've used a lot of reflection.
        In an ordinary codebase that'd be pretty bad, but with obfuscated code like Minecraft's, it's basically
        impossible to maintain.
        That said, we know what minecraft builds look like, so this doesn't need to be maintained,
        and we can feasibly cover all builds.

        As it stands, this method will first search for the Minecraft class.
        the minecraftApplet holds an instance of the Minecraft class, and we have the minecraftApplet,
        so we can search for it there.

        Searching for it involves:
        1. Look for unka field called "minecraft". If it's there use it.
        2. If "minecraft" is not found, find any field within the same package.
           - In every build I've checked, minecraftApplet only has 1 instance variable from the same package,
             and it's Minecraft.

        Then we find the width and height values.
        These are (seemingly) always the first two public integers in the Minecraft class.
        These are obfuscated so cannot be found by name.

        If any of these searches fail, resizing should just do nothing.
    */
    public void appletResize(int width,int height){
        try {
            Field minecraftField = null;

            try {
                minecraftField = minecraftApplet.getClass().getDeclaredField("minecraft");
            } catch (NoSuchFieldException ne) {
                for(Field field : minecraftApplet.getClass().getDeclaredFields()) {
                    if(field.getType().getPackage() == minecraftApplet.getClass().getPackage()) {
                        minecraftField = field;
                        break;
                    }
                }
            }

            Class<?> minecraftClass = minecraftField.getType();

            Field widthField = null;
            Field heightField = null;

            Field guiWidthField = null;
            Field guiHeightField = null;

            // Since Minecraft is obfuscated we can't just get the width and height fields by name.
            // Hopefully, they're always the first two ints. Seems likely.
            for(Field field : minecraftClass.getDeclaredFields()) {
                if ((int.class.equals(field.getType()) || Integer.class.equals(field.getType()))) {
                    if (widthField == null) {
                        widthField = field;
                    } else if (heightField == null) {
                        heightField = field;
                        break;
                    }
                }
            }

            minecraftField.setAccessible(true);
            widthField.setAccessible(true);
            heightField.setAccessible(true);

            Object minecraft = minecraftField.get(minecraftApplet);

            Field guiField = null;

            if (minecraftVersion != null && minecraftVersion.guiClass != null) {
                try {
                    Class guiClass = classLoader.loadClass(minecraftVersion.guiClass);
                    for (Field field : guiClass.getDeclaredFields()) {
                        if ((int.class.equals(field.getType()) || Integer.class.equals(field.getType())) && Modifier.isPrivate(field.getModifiers())) {
                            if (guiWidthField == null) {
                                guiWidthField = field;
                            } else if (guiHeightField == null) {
                                guiHeightField = field;
                                break;
                            }
                        }
                    }

                    guiWidthField.setAccessible(true);
                    guiHeightField.setAccessible(true);

                    for (Field field : minecraftClass.getDeclaredFields()) {
                        if (field.getType().getCanonicalName().equals(minecraftVersion.guiClass)) {
                            guiField = field;
                            guiField.setAccessible(true);
                            break;
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Couldn't find GUI class " + minecraftVersion.guiClass);
                }
            }

            Object gui = guiField != null ? guiField.get(minecraft) : null;

            if (fullscreen){
                heightField.setInt(minecraft, Display.getDisplayMode().getHeight());
                widthField.setInt(minecraft, Display.getDisplayMode().getWidth());

                if(gui != null && guiHeightField != null && guiWidthField != null) {
                    int h = Display.getDisplayMode().getHeight();
                    guiHeightField.setInt(gui, h * 240 / h);
                    guiWidthField.setInt(gui, Display.getDisplayMode().getWidth() * 240 / h);
                }
            }
            else {
                heightField.setInt(minecraft, height - DisplayManager.getFrame().getInsets().top - DisplayManager.getFrame().getInsets().bottom);
                widthField.setInt(minecraft, width);

                if(gui != null && guiHeightField != null && guiWidthField != null) {
                    int h = (height - DisplayManager.getFrame().getInsets().top - DisplayManager.getFrame().getInsets().bottom);
                    guiHeightField.setInt(gui, h * 240 / h);
                    guiWidthField.setInt(gui, width * 240 / h);
                }
            }

            int guiScale = Settings.settings.optInt(Settings.GUI_SCALE, 0);

            if (gui != null && guiHeightField != null && guiWidthField != null && guiScale != 0 && minecraftVersion != null && minecraftVersion.guiScreenClass != null) {
                int scaledWidth;
                int scaledHeight;
                int scaleFactor;

                scaledWidth = Display.getWidth();
                scaledHeight = Display.getHeight();
                scaleFactor = 1;
                int k = guiScale;
                if(k == 0)
                {
                    k = 1000;
                }
                for(; scaleFactor < k && scaledWidth / (scaleFactor + 1) >= 320 && scaledHeight / (scaleFactor + 1) >= 240; scaleFactor++) { }
                scaledWidth = (int)Math.ceil((double)scaledWidth / (double)scaleFactor);
                scaledHeight = (int)Math.ceil((double)scaledHeight / (double)scaleFactor);

                int h = scaledHeight;
                guiHeightField.setInt(gui, h * 240 / h);
                guiWidthField.setInt(gui, scaledWidth * 240 / h);

                guiHeightField.setInt(gui, scaledHeight);
                guiWidthField.setInt(gui, scaledWidth);
            }

            //screenshotLabel.setBounds(30, (AppletH - 16) - 30, 204, 20);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    boolean fullscreen;

    int widthBeforeFullscreen = Display.getWidth();
    int heightBeforeFullscreen = Display.getHeight();

    void setFullscreen(boolean newFullscreen) {
        if(!fullscreen && newFullscreen) {
            widthBeforeFullscreen = Display.getWidth();
            heightBeforeFullscreen = Display.getHeight();
        }
        DisplayManager.fullscreen(newFullscreen);
        fullscreen = newFullscreen;
        if(!fullscreen) {
            appletResize(widthBeforeFullscreen, heightBeforeFullscreen);
            minecraftApplet.setPreferredSize(new Dimension(widthBeforeFullscreen, heightBeforeFullscreen));
            minecraftApplet.resize(new Dimension(widthBeforeFullscreen , heightBeforeFullscreen));
        } else {
            appletResize(DisplayManager.getFrame().getWidth(), DisplayManager.getFrame().getHeight());
            minecraftApplet.setPreferredSize(new Dimension(DisplayManager.getFrame().getWidth(), DisplayManager.getFrame().getHeight()));
            minecraftApplet.resize(new Dimension(DisplayManager.getFrame().getWidth(), DisplayManager.getFrame().getHeight()));
        }

        DisplayManager.getFrame().pack();
    }

    private static ByteBuffer buffer;
    private static byte pixelData[];
    private static int imageData[];
    long lastScreenshotTime = 0;
    String lastScreenshotName = "";

    // this MUST be called from the OpenGL thread.
    public void screenshot() {
        try {
            if(buffer == null || buffer.capacity() < Display.getWidth() * Display.getHeight())
            {
                buffer = BufferUtils.createByteBuffer(Display.getWidth() * Display.getHeight() * 3);
            }
            if(imageData == null || imageData.length < Display.getWidth() * Display.getHeight() * 3)
            {
                pixelData = new byte[Display.getWidth() * Display.getHeight() * 3];
                imageData = new int[Display.getWidth() * Display.getHeight()];
            }
            GL11.glPixelStorei(3333 /*GL_PACK_ALIGNMENT*/, 1);
            GL11.glPixelStorei(3317 /*GL_UNPACK_ALIGNMENT*/, 1);
            buffer.clear();
            if(fullscreen)
                GL11.glReadPixels(0, 0, Display.getDisplayMode().getWidth(), Display.getDisplayMode().getHeight(), 6407 /*GL_RGB*/, 5121 /*GL_UNSIGNED_BYTE*/, buffer);
            else if (minecraftApplet != null)
                GL11.glReadPixels(0, 0, minecraftApplet.getWidth(), minecraftApplet.getHeight(), 6407 /*GL_RGB*/, 5121 /*GL_UNSIGNED_BYTE*/, buffer);
            else
                GL11.glReadPixels(0, 0, Display.getWidth(), Display.getHeight(), 6407 /*GL_RGB*/, 5121 /*GL_UNSIGNED_BYTE*/, buffer);

            buffer.clear();

            buffer.get(pixelData);
            for(int l = 0; l < Display.getWidth(); l++)
            {
                for(int i1 = 0; i1 < Display.getHeight(); i1++)
                {
                    int j1 = l + (Display.getHeight() - i1 - 1) * Display.getWidth();
                    int k1 = pixelData[j1 * 3 + 0] & 0xff;
                    int l1 = pixelData[j1 * 3 + 1] & 0xff;
                    int i2 = pixelData[j1 * 3 + 2] & 0xff;
                    int j2 = 0xff000000 | k1 << 16 | l1 << 8 | i2;
                    imageData[l + i1 * Display.getWidth()] = j2;
                }

            }

            BufferedImage bufferedimage = new BufferedImage(Display.getWidth(), Display.getHeight(), 1);
            bufferedimage.setRGB(0, 0, Display.getWidth(), Display.getHeight(), imageData, 0, Display.getWidth());

            // Copy image to clipboard.
            TransferableImage trans = new TransferableImage( bufferedimage );
            Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
            c.setContents( trans, null );

            if (minecraftVersion.enableScreenshotPatch) {
                File screenshotsFolder = new File(LauncherFiles.MINECRAFT_SCREENSHOTS_PATH);
                screenshotsFolder.mkdirs();

                File file;
                String s = (new StringBuilder()).append(new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date())).toString();
                for(int k = 1; (file = new File(screenshotsFolder, (new StringBuilder()).append(s).append(k != 1 ? (new StringBuilder()).append("_").append(k).toString() : "").append(".png").toString())).exists(); k++) { }

                try {
                    ImageIO.write(bufferedimage, "png", file);
                    System.out.println("Screenshot saved to " + file.getPath());
                    lastScreenshotTime = System.currentTimeMillis();
                    lastScreenshotName = file.getName();
                } catch (IOException e) { e.printStackTrace(); }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Tells the applet that it is active.
    public boolean isActive(){
        return true;
    }

    //This sets the document base URL, which would normally be the URL of the webpage in which the applet was embedded.
    public URL getDocumentBase(){
        String baseURL = "http://www.minecraft.net:80/game/";

        if(minecraftVersion != null && minecraftVersion.baseURLHasNoPort) {
            baseURL = baseURL.replace(":80", "");
        }

        return StringToURL(baseURL);
    }

    //This sets the code base URL, which would normally be defined by the codebase attribute of the <applet> tag.
    public URL getCodeBase(){
        return getDocumentBase();
    }

    //This sets parameters that would normally be set by <param> tags within the applet block defined by <applet> and </applet> tags.
    public String getParameter(String key){
        String value = null;
        switch(key){
            case "stand-alone":
                value = "true";
                break;
            case "username":
                value = Session.session.getUsername();
                break;
            case "sessionid":
                if (!Session.session.isOnline())
                    break;
                value = Session.session.getSessionToken();
                break;
            case "haspaid":
                value = String.valueOf(Settings.settings.getBoolean(Settings.IS_PREMIUM));
                break;
            case "demo":
                value = !Settings.settings.getBoolean(Settings.IS_PREMIUM) ? "true" : null;
                break;
            case "server":
                value = serverAddress;
                break;
            case "port":
                value = serverPort;
                break;
            case "mppass":
                value = MPPass;
                break;
            default:
                //don't do anything
        }
        if (Globals.DEV) {
            if (value == null) {
                System.out.println(key + " = " + "");
            } else {
                System.out.println(key + " = " + value);
            }
        }
        return value;
    }
}

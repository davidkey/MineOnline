package gg.codie.mineonline.gui;

import gg.codie.mineonline.MinecraftVersion;
import gg.codie.mineonline.MinecraftVersionRepository;
import gg.codie.mineonline.Settings;
import gg.codie.mineonline.gui.components.MediumButton;
import gg.codie.mineonline.gui.components.SelectableVersionList;
import gg.codie.mineonline.gui.components.TinyButton;
import gg.codie.mineonline.gui.events.IOnClickListener;
import gg.codie.mineonline.gui.font.GUIText;
import gg.codie.mineonline.gui.rendering.Camera;
import gg.codie.mineonline.gui.rendering.DisplayManager;
import gg.codie.mineonline.gui.rendering.Renderer;
import gg.codie.mineonline.gui.rendering.font.TextMaster;
import gg.codie.mineonline.gui.rendering.shaders.GUIShader;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class SelectVersionMenuScreen implements IMenuScreen {
    MediumButton doneButton;
    MediumButton browseButtonBig;
    TinyButton browseButtonSmall;
    TinyButton backButton;
    GUIText label;

    SelectableVersionList selectableVersionList;

    JFileChooser fileChooser = new JFileChooser();

    // Since browsing doesn't occur on the opengl thread, store the result here and check on update.
    String[] versionToAdd = null;

    public SelectVersionMenuScreen(IOnClickListener backListener, IOnClickListener doneListener, String doneText) {
        doneButton = new MediumButton(doneText != null ? doneText : "Done", new Vector2f((DisplayManager.getDefaultWidth() / 2) + 8, DisplayManager.getDefaultHeight() - 20), doneListener);

        fileChooser.setFileHidingEnabled(false);

        IOnClickListener browseListener = new IOnClickListener() {
            @Override
            public void onClick() {
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        int returnVal = fileChooser.showOpenDialog(DisplayManager.getCanvas());

                        if (returnVal == JFileChooser.APPROVE_OPTION) {
                            File file = fileChooser.getSelectedFile();

                            MinecraftVersion minecraftVersion = MinecraftVersionRepository.getSingleton().getVersion(file.getPath());

                            try {
                                if (!MinecraftVersion.isPlayableJar(file.getPath())) {
                                    JOptionPane.showMessageDialog(null, "This jar file is incompatible:\nNo applet or main class found.");
                                    return;
                                }
                            } catch (IOException ex) {
                                JOptionPane.showMessageDialog(null, "This jar file is incompatible:\nFailed to open.");
                                return;
                            }

                            MinecraftVersionRepository.getSingleton().addInstalledVersion(file.getPath());
                            MinecraftVersionRepository.getSingleton().selectJar(file.getPath());

                            if (minecraftVersion != null) {
                                versionToAdd = new String[]{minecraftVersion.name, file.getPath(), minecraftVersion.info};
                            } else {
                                versionToAdd = new String[]{"Unknown Version", file.getPath(), null};
                            }
                        }
                    }
                });
            }
        };

        if (backListener == null) {
            browseButtonBig = new MediumButton("Browse...", new Vector2f((DisplayManager.getDefaultWidth() / 2) - 308, DisplayManager.getDefaultHeight() - 20), browseListener);
        } else {
            browseButtonSmall = new TinyButton("Browse...", new Vector2f((DisplayManager.getDefaultWidth() / 2) - 150, DisplayManager.getDefaultHeight() - 20), browseListener);
            backButton = new TinyButton("Back", new Vector2f((DisplayManager.getDefaultWidth() / 2) - 308, DisplayManager.getDefaultHeight() - 20), backListener);
        }

        selectableVersionList = new SelectableVersionList("version list", new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1), new IOnClickListener() {
            @Override
            public void onClick() {
                MinecraftVersionRepository.getSingleton().selectJar(selectableVersionList.getSelected());
                Settings.saveSettings();
                MenuManager.setMenuScreen(new MainMenuScreen());
            }
        });

        label = new GUIText("Select Version", 1.5f, TextMaster.minecraftFont, new Vector2f(0, 40), DisplayManager.getDefaultWidth(), true, true);

    }

    public String getSelectedPath() {
        return selectableVersionList.getSelected();
    }

    public void update() {
        if (versionToAdd != null) {
            selectableVersionList.addVersion(versionToAdd[0], versionToAdd[1], versionToAdd[2]);
            selectableVersionList.selectVersion(versionToAdd[1]);
            versionToAdd = null;
        }

        doneButton.update();
        if(browseButtonBig != null)
            browseButtonBig.update();
        if(browseButtonSmall != null)
            browseButtonSmall.update();
        if(backButton != null)
            backButton.update();
        selectableVersionList.update();
    }

    public void render(Renderer renderer) {
        GUIShader.singleton.start();
        GUIShader.singleton.loadViewMatrix(Camera.singleton);
        renderer.prepareGUI();

        doneButton.render(renderer, GUIShader.singleton);
        if(browseButtonBig != null)
            browseButtonBig.render(renderer, GUIShader.singleton);
        if(browseButtonSmall != null)
            browseButtonSmall.render(renderer, GUIShader.singleton);
        if(backButton != null)
            backButton.render(renderer, GUIShader.singleton);
        selectableVersionList.render(renderer, GUIShader.singleton);
    }

    public boolean showPlayer() {
        return false;
    }

    public void resize() {
        doneButton.resize();
        if(browseButtonBig != null)
            browseButtonBig.resize();
        if(browseButtonSmall != null)
            browseButtonSmall.resize();
        if(backButton != null)
            backButton.resize();
        selectableVersionList.resize();
    }

    @Override
    public void cleanUp() {
        doneButton.cleanUp();
        if(browseButtonBig != null)
            browseButtonBig.cleanUp();
        if(browseButtonSmall != null)
            browseButtonSmall.cleanUp();
        if(backButton != null)
            backButton.cleanUp();
        selectableVersionList.cleanUp();
        label.remove();
    }
}

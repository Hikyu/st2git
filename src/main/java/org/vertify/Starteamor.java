package org.vertify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.sync.GitImporter;
import org.sync.Log;
import org.sync.util.Config;

import com.starbase.starteam.CheckoutManager;
import com.starbase.starteam.File;
import com.starbase.starteam.Folder;
import com.starbase.starteam.Item;
import com.starbase.starteam.Label;
import com.starbase.starteam.View;
import com.starbase.starteam.ViewConfiguration;

public class Starteamor {
    private View rootView;
    private Label[] viewLabels;

    public Starteamor(View view) {
        this.rootView = view;
        Folder f = this.rootView.getRootFolder();
        f.populateNow(f.getTypeNames().FILE, null, -1);
    }

    public View getView() {
        return rootView;
    }

    public String getViewName() {
        return rootView.getName();
    }

    /**
     * 获取所有视图，包括自己和所有子视图
     * 
     * @return
     */
    public List<View> getAllViews() {
        List<View> views = this.getAllDerivedView();
        views.add(getView());
        return views;
    }

    /**
     * 递归获取所有子视图
     * 
     * @return
     */
    public List<View> getAllDerivedView() {
        List<View> views = new ArrayList<>();
        getDerivedViews(views, rootView);
        return views;
    }

    private void getDerivedViews(List<View> views, View root) {
        View[] derivedViews = root.getDerivedViews();
        for (View view : derivedViews) {
            views.add(view);
            getDerivedViews(views, view);
        }
    }

    public void dispose() {
        rootView.close();
    }

    public int getLabelIdByName(String tagName) {
        if (viewLabels == null) {
            viewLabels = rootView.fetchAllLabels();
        }
        
        for (Label label : viewLabels) {
            String refName = GitImporter.refName(label.getName());
            if (tagName.equals(refName)) {
                return label.getID();
            }
        }
        throw new IllegalStateException("没有找到 " + tagName + " !!");
    }

    public void checkoutByLabel(int labelBase, java.io.File localDir) throws IOException {
        View vc = new View(rootView, ViewConfiguration.createFromLabel(labelBase));
        CheckoutManager cm = new CheckoutManager(vc);
        cm.getOptions().setEOLConversionEnabled(false);
        cm.getOptions().setUpdateStatus(false);

        Folder root = vc.getRootFolder();
        doFilePopulation(cm, localDir.getCanonicalPath(), root);
    }

    private void doFilePopulation(CheckoutManager cm, String basePath, Folder f) throws IOException {
        for (Item i : f.getItems(f.getTypeNames().FILE)) {
            if (i instanceof File) {
                File historyFile = (File) i;
                String fileName = historyFile.getName();
                String path = basePath + (basePath.length() > 0 ? "/" : "") + fileName;
                // 排除一些文件
                if (checkExcludeFile(fileName)) {
                    continue;
                }
                java.io.File localFile = new java.io.File(path);
                if (!localFile.exists()) {
                    java.io.File parentFile = localFile.getParentFile();
                    if (!parentFile.exists()) {
                        parentFile.mkdirs();
                    }
                    localFile.createNewFile();
                }
                cm.checkoutTo(historyFile, localFile);
            }
        }
        for (Folder subfolder : f.getSubFolders()) {
            String folderName = subfolder.getName();
            // 排除一些文件夹，不计入commit
            if (checkExcludeFolder(folderName)) {
                continue;
            }
            String newBasePath = basePath + (basePath.length() > 0 ? "/" : "") + folderName;
            doFilePopulation(cm, newBasePath, subfolder);
        }
    }

    private boolean checkExcludeFile(String fileName) {
        String excludeFiles = Config.instance.get("excludeFiles");
        if (excludeFiles != null) {
            String[] excludeFile = excludeFiles.split(",");
            for (String file : excludeFile) {
                if (fileName.endsWith(file)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkExcludeFolder(String folderName) {
        String excludeFolders = Config.instance.get("excludeFolders");
        if (excludeFolders != null) {
            String[] excludeFolder = excludeFolders.split(",");
            for (String folder : excludeFolder) {
                if (folderName.equals(folder)) {
                    return true;
                }
            }
        }
        return false;
    }
}

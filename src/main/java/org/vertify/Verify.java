package org.vertify;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.ListTagCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.sync.Log;

import com.starbase.starteam.Project;
import com.starbase.starteam.Server;
import com.starbase.starteam.View;

/**
 * 验证 git-starteam 导入的正确性。 
 * 大体思路： 对于每一条分支，取出其每个 label 的内容，拉取st上相应的内容进行对比
 * 
 * @author Yukai
 *
 */
public class Verify {
    private Gitor gitor;
    private Starteamor stor;
    private Repository repo;
    
    public Verify(String gitProject, View stRootView) throws IOException {
        try {
            repo = new FileRepositoryBuilder().setGitDir(new File(gitProject)).build();
        } catch (IOException e) {
            throw new IOException("不是有效的git仓库！", e);
        }
        this.gitor = new Gitor(repo);
        this.stor = new Starteamor(stRootView);
    }

    public void verify() {
        Log.log(">>>>>>>>>>>>>>>>Start Verify>>>>>>>>>>>>>>>>>>>");
        // 获取所有分支 和 label
        HashMap<String, List<String>> branchLabels = gitor.getBranchLabels();
        // TODO 考虑使用多线程并发
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletionService<Boolean> completionService = new ExecutorCompletionService<Boolean>(executor); 
        
        List<View> allViews = stor.getAllViews();
        for (View view : allViews) {
            String viewName = view.getName();
            if (branchLabels.containsKey(viewName)) {// 分支名与视图名一致
                List<String> tags = branchLabels.get(viewName);
                completionService.submit(new BrViewCompartor(gitor.getRepo(), view, tags));
            } else {
                Log.log(viewName + " not imported!");
            }
        }
        for (int i = 0; i < allViews.size(); i++) {
            String vname = allViews.get(i).getName();
            try {
                if (completionService.take().get()) {
                    Log.log(vname + " impoted correct!");
                } else {
                    Log.log(vname + " impoted uncorrect!!!");
                }
            } catch (InterruptedException e) {
                Log.log(e.getMessage());
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        executor.shutdown();
        gitor.checkout(stor.getViewName());
        dispose();
    }
    
    public void dispose() {
        gitor.dispose();
        stor.dispose();
        repo.close();
    }

    /**
     * st视图与git分支的对比
     * 
     * @author Yukai
     *
     */
    class BrViewCompartor implements Callable<Boolean> {
        private Gitor gitor;
        private Starteamor stor;
        private List<String> tags;

        public BrViewCompartor(Repository repo, View view, List<String> tags) {
            this.tags = tags;
            this.gitor = new Gitor(repo);
            this.stor = new Starteamor(view);
        }

        @Override
        public Boolean call() throws Exception {
            boolean isEqual = true;
            String currentLabel = null;
            File tempStDir = null;
            try {
                Log.log("Verify View: " + this.stor.getViewName());
                for (String tag : tags) {
                    currentLabel = tag;
                    Log.log(" label: " + currentLabel);
                    // git 仓库 check 到指定tag
                    this.gitor.checkout(tag);
                    // 下载st对应view的指定tag
                    tempStDir = Files.createTempDirectory(this.gitor.getTagOnly(tag) + "_").toFile();
                    this.stor.checkoutByLabel(
                            this.stor.getLabelIdByName(this.gitor.getTagOnly(tag)),
                            tempStDir);
                    // 对比文件夹
                    try {
                        Comparators.isDirsAreEqual(this.gitor.getRepo().getWorkTree(), tempStDir);
                    } catch (IOException e) {
                        isEqual = false;
                        Log.log("View：" + this.stor.getViewName() + ", label：" + currentLabel + " is not Equal！");
                        Log.log(e.getMessage());
                    }
                    try {FileUtils.deleteDirectory(tempStDir);} catch (IOException e) {}
                }
            } finally {
                this.stor.dispose();
                this.gitor.dispose();
            }
            return isEqual;
        }

    }

    public static void main(String[] args) {
        String project = "oscartools";
        String view = "oscarJDBC_V1.0";
        String gitRepo = "D:\\st-git\\repo\\up";

        Server starteam = new Server("192.168.101.4", 49201);
        starteam.connect();
        int userid = starteam.logOn("duhuaiyu", "1984114");
        if (userid > 0) {
            for (Project p : starteam.getProjects()) {
                if (p.getName().equalsIgnoreCase(project)) {
                    for (View v : p.getViews()) {
                        if (v.getName().equalsIgnoreCase(view)) {
                            try {
                                new Verify(gitRepo, v).verify();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }
}

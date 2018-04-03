package org.vertify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class Gitor {
    private Repository repo;
    private Git git;

    public Gitor(Repository repo) {
        this.repo = repo;
        this.git = new Git(repo);
    }

    /**
     * 获取所有分支和其对应的所有label
     * 
     * @return key: 分支名 value: 该分支下的所有label
     */
    public HashMap<String, List<String>> getBranchLabels() {
        Set<String> tagRef = new HashSet<>();
        HashMap<String, List<String>> branchLabels = new HashMap<>();
        try {
            List<Ref> branchs = git.branchList().call();
            for (Ref ref : branchs) {// 获取所有分支
                String fullTag = ref.getName();
                if ("HEAD".equals(fullTag)) {
                    continue;
                }
                String prefix = "refs/heads/";
                String br = fullTag.substring(prefix.length());
                branchLabels.put(br, new ArrayList<>());
            }
            List<Ref> tags = git.tagList().call();
            for (Ref ref : tags) {// 获取分支上的tag
                String fullTag = ref.getName();
                String prefix = "refs/tags/";
                String[] brAndtag = fullTag.substring(prefix.length()).split("/");
                if (tagRef.add(getCommitIDFromTag(ref))) {// 过滤掉指向同一个commit的tag，只比较一次即可
                    branchLabels.get(brAndtag[0]).add(fullTag);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return branchLabels;
    }
    
    public static String getTagOnly(String fullTag) {
        String prefix = "refs/tags/";
        String[] brAndtag = fullTag.substring(prefix.length()).split("/");
        return brAndtag[1];
    }

    private String getCommitIDFromTag(Ref tag) {
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(tag.getObjectId());
            return commit.name();
        } catch (IOException e) {
        }
        return "";
    }

    public Repository getRepo() {
        return repo;
    }
    
    /**
     * 切换到指定提交
     * @param commit e.g refs/tags/my-tag
     */
    public void checkout(String commit) {
        try {
            git.checkout().setName(commit).call();
        } catch (GitAPIException e) {
            throw new IllegalStateException(e);
        }
    }
    
    public void dispose() {
        git.close();
    }
}

/*****************************************************************************
    This file is part of Git-Starteam.

    Git-Starteam is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Git-Starteam is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Git-Starteam.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.sync.commitstrategy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.util.StringUtils;
import org.sync.CommitPopulationStrategy;
import org.sync.Log;
import org.sync.RenameFinder;
import org.sync.RepositoryHelper;
import org.sync.util.CommitInformation;
import org.sync.util.Config;
import org.sync.util.HttpClient;
import org.sync.util.Pair;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.starbase.starteam.File;
import com.starbase.starteam.Folder;
import com.starbase.starteam.Item;
import com.starbase.starteam.ItemList;
import com.starbase.starteam.Label;
import com.starbase.starteam.PropertyNames;
import com.starbase.starteam.RecycleBin;
import com.starbase.starteam.ServerException;
import com.starbase.starteam.Type;
import com.starbase.starteam.View;

import jdk.nashorn.internal.parser.JSONParser;
import net.sf.json.JSONObject;

public class BasePopulationStrategy implements CommitPopulationStrategy {

	/// View on which operation of file population will take place.
	protected View currentView;
	protected HashSet<String> lastFiles;
	protected HashSet<String> deletedFiles;
	protected TreeMap<CommitInformation, File> currentCommitList;
	
	private RepositoryHelper helper;
	private int initialFileVersion;
	protected java.util.Date earliestTime;
	
	protected boolean verbose;
	
	/**
	 * Base Population strategy constructor using a view as its base of operations
	 * to iterate around the files to construct the list of commit to reproduce in
	 * git. The algorithms are pretty generic and have plenty of override point to
	 * complet with more information.
	 * 
	 * @param view
	 *          The view where we shall collect ours commits informations
	 */
	public BasePopulationStrategy(View view) {
		currentView = view;
		lastFiles = new HashSet<String>();
		deletedFiles = new HashSet<String>();
		currentCommitList = new TreeMap<CommitInformation, File>();
		verbose = false;
		earliestTime = new java.util.Date(0);
		// We register new files with version -1 to be sure to add it. Since this is
		// a discovered file, when we are going to pass trough the files, we will
		// make sure to get it's version 1. Setting the following value to 0 would
		// grab all the version of the files since its creation
		initialFileVersion = -1;
	}

	@Override
	public void filePopulation(String head, Folder root) {
		currentCommitList.clear(); // flush every composed commit from last run.
		deletedFiles.clear();
		deletedFiles.addAll(lastFiles);
		populateStarteamProperties(root);
		doFilePopulation(head, "", root);
		lastFiles.removeAll(deletedFiles); // clean files that was never seen from the last files.
		recoverDeleteInformation(head, root);
		if (currentCommitList.size() > 0) {
			setLastCommitTime(currentCommitList.lastKey().getCommitDate());
		}
	}

	/**
	 * @param root
	 *          The root folder requiring the properties population
	 */
	protected void populateStarteamProperties(Folder root) {
		PropertyNames propNames = root.getPropertyNames();
		// Those are the interesting properties that we need.
		// Those will prevent back-and-forth with the server regarding the
		// collection of information.
		String[] populateProps = new String[] {
				propNames.FILE_NAME,
				propNames.COMMENT,
				propNames.FILE_DESCRIPTION,
				
				propNames.FILE_CONTENT_REVISION,
				propNames.MODIFIED_TIME,
				propNames.MODIFIED_USER_ID,
				propNames.EXCLUSIVE_LOCKER,
				propNames.NON_EXCLUSIVE_LOCKERS,
				propNames.FILE_ENCODING,
				propNames.FILE_EOL_CHARACTER,
				propNames.FILE_EXECUTABLE,
		    propNames.PATH_REVISION,
		};
		root.populateNow(currentView.getServer().getTypeNames().FILE, populateProps, -1);
	}

	/**
	 * Find out all files to be added into the commit list.
	 * 
	 * @param head
	 *          the head in which we should check for file content modification
	 * @param gitpath
	 *          the current path based on the root folder
	 * @param f
	 *          the folder to grab files from
	 */
	protected void doFilePopulation(String head, String gitpath, Folder f) {
		if (null == head) {
			throw new NullPointerException("Head cannot be null");
		}
		if (null == f) {
			throw new NullPointerException("Folder cannot be null");
		}
		for(Item i : f.getItems(f.getTypeNames().FILE)) {
			if(i instanceof File) {
				File historyFile = (File) i;
				String fileName = historyFile.getName();
				String path = gitpath + (gitpath.length() > 0 ? "/" : "") + fileName;
				// 排除一些文件，不计入commit
				if (checkExcludeFile(fileName)) {
                    continue;
                }
				// 对于文件的每个版本作为一次 commitinformation
				processFileForCommit(head, historyFile, path);
			} else {
				Log.log("Item " + f + "/" + i + " is not a file");
			}
		}
		for(Folder subfolder : f.getSubFolders()) {
			String folderName = subfolder.getName();
			// 排除一些文件夹，不计入commit
			if (checkExcludeFolder(folderName)) {
                continue;
            }
			String newGitPath = gitpath + (gitpath.length() > 0 ? "/" : "") + folderName;
			doFilePopulation(head, newGitPath, subfolder);
		}
	}
	
	private boolean checkExcludeFolder(String folderName) {
	    String excludeFolders = Config.instance.get("excludeFolders");
	    if (excludeFolders != null && !"".equals(excludeFolders)) {
            String[] excludeFolder = excludeFolders.split(",");
            for (String folder : excludeFolder) {
                if (folderName.equals(folder)) {
                    return true;
                }
            }
        }
	    return false;
	}
	
	private boolean checkExcludeFile(String fileName) {
        String excludeFiles = Config.instance.get("excludeFiles");
        if (excludeFiles != null && !"".equals(excludeFiles)) {
            String[] excludeFile = excludeFiles.split(",");
            for (String file : excludeFile) {
                if (fileName.endsWith(file)) {
                    return true;
                }
            }
        }
        return false;
    }
	

	/***
	 * Process an individual file to create a commit in relation with its
	 * modifications
	 * 
	 * @param head
	 *          The target branch name
	 * @param historyFile
	 *          The file from the history we need to process
	 * @param path
	 *          The path where the file will be located in the git repository
	 */
	protected void processFileForCommit(String head, File historyFile, String path) {
		Integer fileid = helper.getRegisteredFileId(head, path);
		Integer previousVersion = -1;
		Integer previousContentVersion = -1;
		if (null == fileid) {
			helper.registerFileId(head, path, historyFile.getItemID(), initialFileVersion, historyFile.getContentVersion(), historyFile.getMD5());
		} else {
			// fetch the previous version we did register so we continue
			// modification from that point in time.
			previousVersion = helper.getRegisteredFileVersion(head, path);
			previousContentVersion = helper.getRegisteredFileContentVersion(head, path);
		}
		if (deletedFiles.contains(path)) {
			deletedFiles.remove(path);
		}
		if (!lastFiles.contains(path)) {
			lastFiles.add(path);
		}
		// prefer content version as it is cached in the File object
		int itemViewVersion = historyFile.getContentVersion();
        //Log.logf("File %s id: %d", path, historyFile.getItemID());
        if (fileid != null && fileid != historyFile.getItemID()) {
            if (previousVersion <= historyFile.getViewVersion()) {
                File fromHistory = (File) historyFile.getFromHistoryByVersion(previousVersion);
                if (fromHistory == null) {
                	Log.logf("pre version: %d cur version: %d", previousVersion, historyFile.getViewVersion());
                }
                else if (Arrays.equals(helper.getRegisteredFileMd5(head, path), fromHistory.getMD5())) {
                    // same file, but item id changed
                    Log.logf("File %s's id was changed from %d to %d, but the file is not replaced", 
                            path, fileid, historyFile.getItemID());
                    fileid = historyFile.getItemID();
                    helper.updateFileId(head, path, fileid);
                }
            }
        }
		if (fileid != null && fileid != historyFile.getItemID()) {
			Log.logf("File %s was replaced", path);			
			createCommitInformation(path, historyFile, 1);
		} else if (previousContentVersion > itemViewVersion) {
			Log.logf("File %s was reverted from version %d to %d was skipped", path, previousContentVersion,
					itemViewVersion);
			createCommitInformation(path, historyFile, 1);
		} else {
			// To get a better feel of all modification that did occurs in the history, get each version of the files that we
			// didn't see in the past.
			int iterationCounter = 1;
			int viewVersion = historyFile.getViewVersion();
			for (int ver = previousVersion + 1; ver <= viewVersion;) {// 自上次访问到的文件版本到此次文件版本之间的所有文件版本，都需要形成一个 commitinformation
				// If theirs is a concurrent acces to the file, we need to retry again
				// later.
				try {
					File fromHistory = (File) historyFile.getFromHistoryByVersion(ver);
					if (fromHistory != null) {
						// iterationCounter only serve as an helper in case the multiple
						// version of the same file are done in the past
						createCommitInformation(path, fromHistory, iterationCounter++);
					} else {
//						Log.logf("File %s doesn't have a view version #%d, started iteration at version %d", path, ver,
//						    previousVersion + 1);
					}
					ver++;
				} catch (ServerException e) {
					Log.logf("Failed to get revision %d of file %s will try again", ver, path);
					try {
						Thread.sleep(100);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				}
			}
		}
	}

	/**
	 * This method make sure that the found item is part of the current root we
	 * are scanning. Sometime, when a file is still in the view but is present
	 * somewhere else in the view but not as a sub element of the current root.
	 *
	 * This simple iterative method should be able to confirm the file is a child
	 * of the requested folder.
	 *
	 * @param aFile
	 *          the leaf file we are querying
	 * @param aPotentialParent
	 *          the expected parent to find
	 * @return true if the file is the child of the provided parent.
	 **/
	protected boolean isChildOf(File aFile, Folder aPotentialParent) {
		Folder parent = aFile.getParentFolder();
		int searchedVersion = aPotentialParent.getViewVersion();
		while (parent != null) {
			if (parent.getObjectID() == aPotentialParent.getObjectID()) {
				return parent.getViewVersion() == searchedVersion;
			}
			parent = parent.getParentFolder();
		}
		return false;
	}
	
	/**
	 * This method is used to extract the pathname based on the root of the
	 * conversion that we are doing. The path is created by backtracking the
	 * parent until we find the provided root.
	 * 
	 * @param aFile
	 *          the file we need to find the correct path from
	 * @param root
	 *          the base folder to which we need to backtrack the path
	 * @return a git path to the file
	 */
	private String pathname(File aFile, Folder root) {
		ArrayList<CharSequence> pathComponent = new ArrayList<CharSequence>();
		pathComponent.add(0, aFile.getName());
		Folder parent = aFile.getParentFolder();
		boolean foundCommonParent = false;
		while (parent != null) {
			if (parent.getObjectID() == root.getObjectID()) {
				foundCommonParent = true;
				break;
			}
			pathComponent.add(0, parent.getName());
			parent = parent.getParentFolder();
		}
		if (!foundCommonParent) {
			throw new RuntimeException("Could not find the comment path between " + 
					aFile.getParentFolderHierarchy() + "/" + aFile.getName() + 
					" and " + root.getName());
		}
		StringBuilder joiner = new StringBuilder();
		joiner.append(pathComponent.get(0));
		for (int i = 1; i < pathComponent.size(); i++) {
			joiner.append("/").append(pathComponent.get(i));
		}
		return joiner.toString();
	}

	/**
	 * Determines what happened to files which were in the view during the
	 * previous recursiveFilePopulation run but were not found in the current run.
	 *
	 * Deleted files will be found in the view recycle bin. Otherwise the file was
	 * renamed.
	 *
	 * @param head
	 *          which git head we are refering to
	 * @param root
	 *          base root folder we are importing from
	 */
	private void recoverDeleteInformation(String head, Folder root) {
		RecycleBin recycleBin = null;
		Type fileType = currentView.getServer().typeForName(currentView.getTypeNames().FILE);
		try {
			recycleBin = root.getView().getRecycleBin();
			recycleBin.setIncludeDeletedItems(true);
			fileType = currentView.getServer().typeForName(recycleBin.getTypeNames().FILE);
		} catch (java.lang.UnsupportedOperationException e) {
			recycleBin = null;
			fileType = currentView.getServer().typeForName(currentView.getTypeNames().FILE);
		}

		RenameFinder renameFinder = new RenameFinder();
		
		ArrayList<Pair<String, File>> deletedpaths = new ArrayList<Pair<String, File>>(deletedFiles.size());

		// No need to call populateNow on recycleBin.getRootFolder(), as that
		// is done by recycleBin.findItem(). If we called it now, we would
		// incur a long wait which we may not need.
		for(Iterator<String> ith = deletedFiles.iterator(); ith.hasNext(); ) {
			String path = ith.next();
			Integer fileID = helper.getRegisteredFileId(head, path);
			if(null != fileID) {
				File item = null;
				if (null == recycleBin ) {
					item = null;
				} else {
					try {
						item = (File) recycleBin.findItem(fileType, fileID);
					} catch (ServerException e) {
						Log.logf("Coulfd not find deleted files <%s> ID: %d [%s]", path, fileID, e.getMessage());
					}
				}
				if(null != item && item.isDeleted()) {
					deletedpaths.add(new Pair<String, File>(path, item));
					ith.remove();
				} else {
					item = (File) root.getView().findItem(fileType, fileID);
					if(null != item && isChildOf(item, root)) {
						CommitInformation deleteInfo;
						String newPath = pathname(item, root);
						Item renameEventItem = renameFinder.findEventItem(currentView, path, newPath, item,
						    item.getModifiedTime().getLongValue());
						if(null != renameEventItem) {
							if (verbose) {
								Log.logf("Renamed %s -> %s at %s",
								    path, newPath,
										renameEventItem.getModifiedTime());
							}
							deleteInfo = new CommitInformation(renameEventItem.getModifiedTime().createDate(),
									renameEventItem.getModifiedBy(),
									"",
									path);
						} else {
							// if it isn't a rename, must be a move operation.
							if (verbose) {
								Log.logf("No rename event found: %s -> %s something has moved", path, newPath);
							}
							declareEarlierCommitAsMoved(item, newPath);

							// Not sure how this happens, but fill in with the
							// only information we have: the last view time
							// and the last person to modify the item.
							deleteInfo = new CommitInformation(item.getModifiedTime().createDate(), item.getModifiedBy(), "", path);
						}
						deleteInfo.setFileDelete(true);
						ith.remove();
						// Cause old file to be deleted..

						currentCommitList.put(deleteInfo, item);
						// Replace the existing entries for item if they have an earlier timestamp.
						CommitInformation info = new CommitInformation(deleteInfo.getCommitDate(), deleteInfo.getUid(), "", newPath);
						replaceEarlierCommitInfo(info, item, root);
					}
				}
			} else {
				Log.log("Never seen the file " + path + " in " + head);
			}
		}
		
		if (deletedpaths.size() > 0) {
			ItemList items = new ItemList();
			for (int i = 0; i < deletedpaths.size(); i++) {
				items.addItem(deletedpaths.get(i).getSecond());
			}
			PropertyNames propNames = currentView.getPropertyNames();
			String[] populateProps = new String[] {
					propNames.FILE_NAME,
					PropertyNames.ITEM_DELETED_TIME,
					PropertyNames.ITEM_DELETED_USER_ID,
			};
			try {
				items.populateNow(populateProps);
			} catch (com.starbase.starteam.NoSuchPropertyException e) {
				Log.log("Could not populate the deleted files information");
			}
			for (int i = 0; i < deletedpaths.size(); i++) {
				File item = deletedpaths.get(i).getSecond();
				CommitInformation info = new CommitInformation(item.getDeletedTime().createDate(),
						item.getDeletedUserID(),
						"",
						deletedpaths.get(i).getFirst());
				if (verbose) {
					Log.logf("Deleting %s at %d", deletedpaths.get(i).getFirst(), item.getDeletedTime().getLongValue());
				}
				info.setFileDelete(true);
				// Deleted files won't have entries, so add one here to make the delete end up in a commit.
				currentCommitList.put(info, item);
			}
		}
	}
	
	/**
	 * Find a earlier commit done that match with the given item and path to
	 * declare it as an unexpected move operation
	 * 
	 * @param item
	 *          The file that was detected as moved somewhere else
	 * @param newPath
	 *          The path to check for
	 */
	private void declareEarlierCommitAsMoved(File item, String newPath) {
		CommitInformation replacement = null;
		File originalValue = null;
		for (Iterator<Map.Entry<CommitInformation, File>> it = currentCommitList.entrySet().iterator(); it.hasNext();) {
			Entry<CommitInformation, File> entry = it.next();
			if (entry.getKey().getPath().equals(newPath) && item.getObjectID() == entry.getValue().getObjectID()) {
				originalValue = entry.getValue();
				// Time need to match with the delete instruction to be combined
				// together
				replacement = new CommitInformation(earliestTime, entry.getKey().getUid(), "Unexpected Move",
				    newPath);
				it.remove();
				break;
			}
		}
		if (replacement != null) {
			currentCommitList.put(replacement, originalValue);
		}

	}

	/**
	 * Remove duplicate commit information from the current commit list in such
	 * preventing sending too much information to git and requiring too much
	 * information to Starteam.
	 *
	 * @param info
	 *          commit that is replacing
	 * @param file
	 *          The file which is being targeted
	 * @param root
	 *          The root folder on which the importation is based on
	 */
	private void replaceEarlierCommitInfo(CommitInformation info, File file, Folder root) {
		String path = pathname(file, root);
		// TODO: a better data structure for fileList would make this more efficient.
		for(Iterator<Map.Entry<CommitInformation, File>> ith = currentCommitList.entrySet().iterator(); ith.hasNext(); ) {
			CommitInformation info2 = ith.next().getKey();
			if (path.equals(info2.getPath()) && info2.getCommitDate().before(info.getCommitDate())) {
				ith.remove();
			}
		}
	}
	
	/**
	 * Correct the comment based on a set of basic rule against the file.
	 * 
	 * @param historyFile
	 *          Starteam history file on which the comment be generated.
	 * @return a corrected and trimmed comment
	 */
	protected String correctedComment(File historyFile) {
		String comment = historyFile.getComment().trim();
		if(0 == comment.length()) { // if the file doesn't have comment
			if(1 == historyFile.getContentVersion()) {
				comment = historyFile.getDescription().trim();
			}
		} else if(comment.matches("Merge from .*?, Revision .*")) {
			comment = "Merge from unknown branch";
		}
		return comment;
	}
	
	/**
	 * Create a commit information based on the path and history file
	 * 
	 * @param path
	 *          The target path in git repository
	 * @param fileToCommit
	 *          The file the commit is based on
	 * @param iterationCounter
	 *          Counter helper to correct the commit date forward 1 second based
	 *          on the last filePopulation pass
	 */
	protected void createCommitInformation(String path, File fileToCommit, int iterationCounter) {
		String comment = correctedComment(fileToCommit);
		String realAuthor = getRealAuthor(comment);
		String commentWithFormatBugId = getCommentWithFormatBugId(comment);
		String realComment = getRealComment(commentWithFormatBugId);
		Date authorDate = new java.util.Date(fileToCommit.getModifiedTime().getLongValue());
		Date commitDate = calculateCommitDate(authorDate, iterationCounter);

		CommitInformation info = new CommitInformation(commitDate, fileToCommit.getModifiedBy(), realComment, path);
		info.setAuthorDate(authorDate);
		info.setUname(realAuthor);
		if (verbose) {
			Log.log("Discovered commit <" + info + ">");
		}
		currentCommitList.put(info, fileToCommit);
	}
	
	/**
	 * 将注释中的 bug id 替换为 gitlab 可识别的 bug id
	 * 
	 * 比如：To fix TD bug21581_bug123545_YF-20180909-001 by yukai : 将oscarJDBC.jar打包进其他jar包执行时，抛出未捕获异常
     *      Review Link : http://192.168.101.27/r/8199/
     *      
     * 替换后：To fix TD bug #21581 bug #12345 req #30768(YF-20180909-001) by yukai : 将oscarJDBC.jar打包进其他jar包执行时，抛出未捕获异常
     *      Review Link : http://192.168.101.27/r/8199/
     * 
	 * @param comment
	 * @return
	 */
	private static String getCommentWithFormatBugId(String comment) {
	    String commentWithFormatBugId = comment;
        // 匹配 bugXXX BugXXX BUGXXX
        commentWithFormatBugId = recursiveCheckBugTag(commentWithFormatBugId);
        commentWithFormatBugId = recursiveCheckReqTag(commentWithFormatBugId);
        // 将连续的bugID之间的"_"替换为" "
        String pattern = "To\\s+fix\\s+.+by\\s+\\w+";
        Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(commentWithFormatBugId);
        if (matcher.find()) {
            String reviewTag = matcher.group(0);
            // 去掉 "TD"
            Matcher tdMatcher = Pattern.compile("TD\\s+", Pattern.CASE_INSENSITIVE).matcher(reviewTag);
            if (tdMatcher.find()) {
                reviewTag = tdMatcher.replaceFirst("");
            }
            // 去掉 by XXX
            Matcher byMatcher = Pattern.compile("\\s+by\\s+\\w+", Pattern.CASE_INSENSITIVE).matcher(reviewTag);
            if (byMatcher.find()) {
                reviewTag = byMatcher.replaceFirst("");
            }
            commentWithFormatBugId = matcher.replaceFirst(Matcher.quoteReplacement(reviewTag.replaceAll("_", " ")));
        }
        return commentWithFormatBugId;
	}
	
	private static String recursiveCheckReqTag(String comment) {
	    String pattern = "[A-Z]{2}-\\d{8}-\\d{3}";
        Matcher matcher = Pattern.compile(pattern).matcher(comment);
        if (matcher.find()) {
             String reqTag = matcher.group(0);
             String formatComment = matcher.replaceFirst(Matcher.quoteReplacement(getFormatReqId(reqTag)));
             return recursiveCheckReqTag(formatComment);
        } else {
            return comment;
        }
	}
	
	private static String recursiveCheckBugTag(String comment) {
	    String pattern = "(bug|td)\\s*\\d+((\\s+|_|(\\s*(&|、|和|,|，)\\s*))\\d+)*";
        Matcher matcher = Pattern.compile(pattern,Pattern.CASE_INSENSITIVE).matcher(comment);
        if (matcher.find()) {
             String bugTag = matcher.group(0);
             String formatComment = matcher.replaceFirst(Matcher.quoteReplacement(getFormatBugId(bugTag)));
             return recursiveCheckBugTag(formatComment);
        } else {
            return comment;
        }
	}
	
	private static String getFormatReqId(String reqName) {
	    StringBuilder sBuilder = new StringBuilder("req");
	    List<String> seqList = Config.instance.getReqSeqbyReqName(reqName);
	    if (seqList.isEmpty()) {
	        reqName = reqName.replaceAll("-", "--");// 防止无限递归
	        return reqName ;
        } else {
            for (int i = 0; i < seqList.size(); ++i) {
                int reqNumber = Config.instance.getTdReqStartNumber() + Integer.valueOf(seqList.get(i));
                sBuilder.append("#").append(String.valueOf(reqNumber));
                if (i < seqList.size() - 1) {
                    sBuilder.append("|");
                }
            }
//            sBuilder.append("(").append(reqName).append(")");
            return sBuilder.toString();
        }
	}
	
	private static String getFormatBugId(String bugId) {
	    StringBuilder sBuilder = new StringBuilder("bug");
	    String pattern = "\\d+";
	    Matcher matcher = Pattern.compile(pattern).matcher(bugId);
        while (matcher.find()) {
             sBuilder.append("#");
             String bug = matcher.group();
             sBuilder.append(bug).append(" ");
        } 
        return sBuilder.toString();
	}
	
	/**
     * 从注释中识别出完整的注释信息
     * 
     * 比如：To fix TD bug21581 by yukai : 将oscarJDBC.jar打包进其他jar包执行时，抛出未捕获异常
     *      Review Link : http://192.168.101.27/r/8199/
     *      
     * 完整的注释信息在 Review Link : http://192.168.101.27/r/8199/ 页面当中，将这部分注释也添加进来。完整注释如下：
     * 
     * To fix TD bug21581 by yukai : 将oscarJDBC.jar打包进其他jar包执行时，抛出未捕获异常
     * 将oscarJDBC.jar打包进其他jar包执行时，抛出未捕获异常
     * Config.init 方法中读取了jar包或class所在的路径。当oscarJDBC.jar被打包进其他jar包执行时，无法访问该路径导致抛出异常。
     * 此异常应该被捕获并忽略。
     * 
     * 
     * review board API : http://192.168.101.27/api/review-requests/{review id} 取得xml格式的review详细信息
     * 
     * @return
     */
	private static String getRealComment(String comment) {
	    StringBuilder realComment = new StringBuilder(comment);
	    String pattern = "(Review\\s+)*(Link\\s+:\\s+)*[hH][tT]{2}[pP]://((192.168.101.27)|(10.0.5.169))/r/\\d+/*";
        Matcher matcher = Pattern.compile(pattern,Pattern.CASE_INSENSITIVE).matcher(comment);
        String reviewLink = "";
        int reviewLinkStartIndex = -1;
        while (matcher.find()) {
            reviewLink = matcher.group();
            reviewLinkStartIndex = matcher.start();
        }
        if (!"".equals(reviewLink)) {
            // reviewLinux 之前的部分
            realComment = new StringBuilder(comment.substring(0, reviewLinkStartIndex));
            Matcher seqMatcher = Pattern.compile("\\d+").matcher(reviewLink);
            String seq = "";
            while(seqMatcher.find()) {// 找到需求编号
                seq = seqMatcher.group();
            }
            if (!"".equals(seq)) {
                String reviewBoardAPI = Config.instance.getReviewBoardRestDomain() + seq;
                try {
                    String res = HttpClient.httpRequest(reviewBoardAPI);
                    JSONObject resObj = JSONObject.fromObject(res);
                    JSONObject reviewRequest = JSONObject.fromObject(resObj.getString("review_request"));
                    String description = reviewRequest.getString("description");
                    // 换行并插入空行 插入reviewboard上的描述信息
                    realComment.append("\n").append("\n").append(description);
                } catch (IOException e) {
                    Log.log("connect review board failed " + e.getMessage());
                }
            }
            // 插入reviewboard 连接
            realComment.append("\n").append("\n").append(reviewLink);
        }
        
        // 将 reviewboard 连接替换为域名
        String result = realComment.toString();
        result = result.replaceAll("(192.168.101.27)|(10.0.5.169)", "reviewboard.db.org");
        
        return result;
	    
	}
	
	
	/**
	 * 从注释中识别出真正的提交者
	 * 
	 * 比如：To fix TD bug21581 by yukai : 将oscarJDBC.jar打包进其他jar包执行时，抛出未捕获异常
     *      Review Link : http://192.168.101.27/r/8199/
     *      
     * 真正的提交者为 yukai
     * 
	 * @return
	 */
	private static String getRealAuthor(String comment) {
	    String realUser = null;
	    String pattern = "To\\s+fix\\s+.+by\\s+\\w+";
	    Matcher matcher = Pattern.compile(pattern).matcher(comment);
	    if (matcher.find()) {
            String reviewTag = matcher.group(0);
            realUser = reviewTag.substring(reviewTag.lastIndexOf(" ")).trim();
        }
        return realUser;
	}
	
	/**
	 * 对于同一文件，保证提交时间是线性增长的，防止提交在view label之间错乱。
	 * 
	 * @param authorDate
	 * @param iterationCounter
	 * @return
	 */
	private Date calculateCommitDate(Date authorDate, int iterationCounter) {
	    // This is a patchup time to prevent commit jumping up in time between view labels
	    long timeOfCommit = authorDate.getTime();
        if (earliestTime != null && earliestTime.getTime() >= timeOfCommit) {
            // add offset with last commit to keep order. Based on the last commit
            // from the previous pass + 1 second by counter
            long newTime = earliestTime.getTime() + (1000 * iterationCounter);
//            if (verbose) {
//                Log.logf("Changing commit time of %s from %d to %d", path, timeOfCommit, newTime);
//            }
            timeOfCommit = newTime;
        }
        Date commitDate = new java.util.Date(timeOfCommit);
        return commitDate;
	}
	
	@Override
	public void setInitialPathList(Set<String> initialPaths) {
		lastFiles.addAll(initialPaths);
	}

	@Override
	public NavigableMap<CommitInformation, File> getListOfCommit() {
		return currentCommitList;
	}

	@Override
	public Set<String> pathToDelete() {
		return deletedFiles;
	}

	@Override
	public void setRepositoryHelper(RepositoryHelper helper) {
		this.helper = helper;
	}

	@Override
	public void setLastCommitTime(Date earliestTime) {
		this.earliestTime = earliestTime;
		if (verbose) {
			Log.log("Set earliest commit to do at " + earliestTime);
		}
	}

	@Override
	public void setVerboseLogging(boolean verbose) {
		this.verbose = verbose;
	}

	@Override
	public List<String> getLastFiles() {
		ArrayList<String> ret = new ArrayList<String>();
		ret.addAll(lastFiles);
		return ret;
	}

	@Override
	public void setCurrentLabel(Label current) {
		// The base population strategy isn't interested by this added information
	}

	@Override
	public boolean isTagRequired() {
		// Tag are always welcome with the base strategy
		return true;
	}
	
	public static void main(String[] args) {
	    String comment = "bug td19262 19291、14567&88888和939393，2231,33333 To fix td td19262_19291 by yangyancheng : 移植 from 7.0.7 to 7.0.8:调用包中函数时，结果出错"
    + " Review Link : http://192.168.101.27/r/8199/";
        String realAuthor = getRealAuthor(comment);
        String commentWithFormatBugId = getCommentWithFormatBugId(comment);
        String realComment = getRealComment(commentWithFormatBugId);
        System.out.println(realAuthor);
        System.out.println(realComment);
    }
}

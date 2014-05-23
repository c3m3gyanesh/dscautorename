/**
 * This file is part of DSCAutoRename application.
 * 
 * Copyright (C) 2014 Claudiu Ciobotariu
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ro.ciubex.dscautorename.task;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ro.ciubex.dscautorename.DSCApplication;
import ro.ciubex.dscautorename.model.OriginalData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;

/**
 * An AsyncTask used to rename a file.
 * 
 * @author Claudiu Ciobotariu
 * 
 */
public class RenameFileAsyncTask extends AsyncTask<Void, Void, Integer> {
	private final static String TAG = RenameFileAsyncTask.class.getName();
	private DSCApplication mApplication;
	private ContentResolver mContentResolver;
	private Listener mListener;
	private List<OriginalData> mListFiles;
	private int mPosition, mCount;

	public interface Listener {
		public void onTaskStarted();

		public void onTaskUpdate(int position, int count);

		public void onTaskFinished(int count);
	}

	public RenameFileAsyncTask(DSCApplication application) {
		this(application, null);
	}

	public RenameFileAsyncTask(DSCApplication application, Listener listener) {
		this.mApplication = application;
		this.mListener = listener;
		mApplication.setRenameFileTaskRunning(true);
	}

	/**
	 * Method invoked on the background thread.
	 * 
	 * @param params
	 *            In this case are not used parameters.
	 * @return Is returned a void value.
	 */
	@Override
	protected Integer doInBackground(Void... params) {
		mContentResolver = mApplication.getContentResolver();
		mPosition = 0;
		boolean success;
		String filterPath;
		boolean enableFilter;
		boolean skipFile;
		if (mContentResolver != null) {
			enableFilter = mApplication.isEnabledFolderScanning();
			filterPath = mApplication.getFolderScanning();
			while (mApplication.isRenameFileRequested()) {
				mApplication.setRenameFileRequested(false);
				populateAllListFiles();
				if (!mListFiles.isEmpty()
						&& !mApplication.isRenameFileTaskCanceled()) {
					mPosition = 0;
					publishProgress();
					for (OriginalData data : mListFiles) {
						String oldFileName = data.getData();
						if (oldFileName != null) {
							File oldFile = getFile(oldFileName);
							if (oldFile != null) {
								skipFile = false;
								if (enableFilter) {
									skipFile = !oldFile.getAbsolutePath()
											.startsWith(filterPath);
								}
								if (!skipFile) {
									success = oldFile.canRead()
											&& oldFile.canWrite();
									if (success) {
										success = renameFile(data, oldFile,
												oldFileName);
										if (success) {
											mPosition++;
										} else {
											rollbackMediaStoreData(data);
											Log.d(TAG,
													"rollback: " + data.getId()
															+ ", "
															+ oldFileName);
										}
									} else {
										Log.e(TAG,
												"File is not reable and writable: "
														+ oldFileName);
									}
								} else {
									Log.d(TAG, "Skip rename file: "
											+ oldFileName);
								}
							} else {
								Log.e(TAG, "The file:" + oldFileName
										+ " does not exist.");
							}
						} else {
							Log.e(TAG, "oldFileName is null.");
						}
						publishProgress();
					}
					if (mPosition > 0) {
						mApplication.increaseFileRenameCount(mPosition);
					}
					populateAllListFiles();
				}
				doQuery(mContentResolver,
						MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
				mApplication.setRenameFileTaskRunning(false);
			}
		}
		return mPosition;
	}

	/**
	 * Runs on the UI thread before doInBackground(Params...).
	 */
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		if (mListener != null) {
			mListener.onTaskStarted();
		}
	}

	/**
	 * Runs on the UI thread after publishProgress(Progress...) is invoked. The
	 * specified values are the values passed to publishProgress(Progress...).
	 * 
	 * @param values
	 *            Not used.
	 */
	@Override
	protected void onProgressUpdate(Void... values) {
		super.onProgressUpdate(values);
		if (mListener != null) {
			mListener.onTaskUpdate(mPosition, mCount);
		}
	}

	/**
	 * Runs on the UI thread after doInBackground(Params...). The specified
	 * result is the value returned by doInBackground(Params...).
	 * 
	 * @param count
	 *            The result of the operation computed by
	 *            doInBackground(Params...).
	 */
	@Override
	protected void onPostExecute(Integer count) {
		super.onPostExecute(count);
		if (mListener != null) {
			mListener.onTaskFinished(count);
		}
		if (mListFiles != null) {
			mListFiles.clear();
		}
		mListFiles = null;
		mApplication.setRenameFileTaskCanceled(false);
	}

	/**
	 * Rename the old file with provided new name.
	 * 
	 * @param data
	 *            Original data information.
	 * @param oldFile
	 *            The old file to be renamed.
	 * @param oldFileName
	 *            The old file name.
	 */
	private boolean renameFile(OriginalData data, File oldFile,
			String oldFileName) {
		boolean success = false;
		int index = 0;
		String newFileName;
		File newFile;
		boolean exist = false;
		do {
			newFileName = getNewFileName(data, oldFile, index);
			newFile = new File(oldFile.getParentFile(), newFileName);
			index++;
			exist = newFile.exists();
		} while (exist && index < 1000);
		if (!exist) {
			int id = data.getId();
			success = setNewFileToMediaStoreData(id, data.getUri(),
					oldFileName, newFile);
			if (success) {
				success = oldFile.renameTo(newFile);
				if (!success) {
					Log.e(TAG, "The file " + oldFileName + " (id:" + id
							+ ") cannot be renamed!");
				} else {
					Log.d(TAG, "File renamed: " + id + ", " + oldFileName
							+ ", " + newFileName);
				}
			} else {
				Log.e(TAG, "The file cannot be renamed: " + oldFileName);
			}
		} else {
			Log.e(TAG, "The file cannot be renamed: " + oldFileName + " to "
					+ newFileName);
		}
		return success;
	}

	/**
	 * Update the Media Store data with the new file name.
	 * 
	 * @param id
	 *            The ID of the file to be updated
	 * @param oldFileName
	 *            The old file name.
	 * @param newFileName
	 *            The new file name.
	 * @return True if the file name was updated.
	 */
	private boolean setNewFileToMediaStoreData(int id, Uri uri,
			String oldFileName, File newFile) {
		boolean result = updateMediaStoreData(id, uri,
				newFile.getAbsolutePath(),
				removeExtensionFileName(newFile.getName()), newFile.getName());
		return result;
	}

	/**
	 * Put back the old data info on the DB, usually this is invoked when the
	 * file cannot be renamed.
	 * 
	 * @param data
	 *            The original details object.
	 * @return True if the data information was stored on DB.
	 */
	private boolean rollbackMediaStoreData(OriginalData data) {
		boolean result = updateMediaStoreData(data.getId(), data.getUri(),
				data.getData(), data.getTitle(), data.getDisplayName());
		return result;
	}

	/**
	 * Update media store files with following data details.
	 * 
	 * @param id
	 *            The file ID.
	 * @param data
	 *            The file data, normally this is the file path.
	 * @param title
	 *            The file title, usually is the file name without path and
	 *            extension.
	 * @param displayName
	 *            The file display name, usually is the file name without the
	 *            path.
	 * @return True if the file information was stored on DB.
	 */
	private boolean updateMediaStoreData(int id, Uri uri, String data,
			String title, String displayName) {
		boolean result = false;
		ContentValues contentValues = new ContentValues();
		contentValues.put(MediaStore.MediaColumns.DATA, data);
		contentValues.put(MediaStore.MediaColumns.TITLE, title);
		contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
		try {
			int count = mContentResolver.update(uri, contentValues,
					MediaStore.MediaColumns._ID + "=?",
					new String[] { "" + id });
			result = (count == 1);
		} catch (Exception ex) {
			Log.e(TAG,
					"Cannot be updated the content resolver: " + uri.toString()
							+ " Exception: " + ex.getMessage(), ex);
		}
		return result;
	}

	/**
	 * 
	 * @param fullFilePath
	 * @return
	 */
	private String removeExtensionFileName(String fullFilePath) {
		if (fullFilePath != null && fullFilePath.length() > 0) {
			int idx = fullFilePath.lastIndexOf('.');
			if (idx > -1 && idx < fullFilePath.length()) {
				fullFilePath = fullFilePath.substring(0, idx);
			}
		}
		return fullFilePath;
	}

	/**
	 * Rename the file provided as parameter.
	 * 
	 * @param data
	 *            Original data information.
	 * @param file
	 *            The file to be renamed.
	 * @param index
	 *            The index used to generate a unique file name if the file is
	 *            already exist.
	 */
	private String getNewFileName(OriginalData data, File file, int index) {
		String oldFileName = file.getName();
		long dateadded = data.getDateAdded();
		if (dateadded < 9999999999L) {
			dateadded = dateadded * 1000;
		}
		String newFileName = mApplication.getFileName(new Date(dateadded));
		if (index > 0) {
			newFileName += "_" + index;
		}
		newFileName += getFileExtension(oldFileName);
		return newFileName;
	}

	/**
	 * Obtain the file extension, based on the full file name.
	 * 
	 * @param fileName
	 *            The file name.
	 * @return The file extension.
	 */
	private String getFileExtension(String fileName) {
		String ext = ".jpg";
		int idx = fileName.lastIndexOf(".");
		if (idx > 0) {
			ext = fileName.substring(idx);
		}
		return ext;
	}

	/**
	 * Get the file object based on the name provided.
	 * 
	 * @param fileName
	 *            The file name.
	 * @return The file object.
	 */
	private File getFile(String fileName) {
		File file = new File(fileName);
		if (file.exists() && file.isFile()) {
			return file;
		}
		return null;
	}

	/**
	 * Populate the list files accordingly with user choice.
	 */
	private void populateAllListFiles() {
		if (mListFiles == null) {
			mListFiles = new ArrayList<OriginalData>();
		} else {
			mListFiles.clear();
		}
		populateListFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		populateListFiles(MediaStore.Images.Media.INTERNAL_CONTENT_URI);
		if (mApplication.isRenameVideoEnabled()) {
			populateListFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
			populateListFiles(MediaStore.Video.Media.INTERNAL_CONTENT_URI);
		}
		mCount = mListFiles.size();
	}

	/**
	 * Obtain a list with all files ready to be renamed.
	 * 
	 * @param uri
	 *            The URI, using the content:// scheme, for the content to
	 *            retrieve.
	 */
	private void populateListFiles(Uri uri) {
		String[] array = mApplication.getOriginalFilePrefix();
		Cursor cursor = null;
		String[] columns = new String[] { MediaStore.MediaColumns._ID,
				MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.TITLE,
				MediaStore.MediaColumns.DISPLAY_NAME,
				MediaStore.MediaColumns.DATE_ADDED };
		StringBuilder where = new StringBuilder("");
		String[] selectionArgs = null;
		String temp;
		int index, length = array.length;
		if (length > 0) {
			selectionArgs = new String[length];
			for (index = 0; index < length; index++) {
				temp = array[index].trim();
				if (temp.length() > 0) {
					if (index > 0) {
						where.append(" OR ");
					}
					where.append(MediaStore.MediaColumns.DATA)
							.append(" LIKE ?");
					selectionArgs[index] = "%" + temp + "%";
				}
			}
		}
		try {
			String selection = null;
			if (where.length() > 0) {
				selection = where.toString();
			} else {
				selectionArgs = null;
			}
			cursor = mContentResolver.query(uri, columns, selection,
					selectionArgs, null);
			if (cursor != null) {
				int id;
				long dateAdded;
				String data, title, displayName;
				OriginalData originalData;
				while (cursor.moveToNext()) {
					id = cursor.getInt(cursor
							.getColumnIndex(MediaStore.MediaColumns._ID));
					data = cursor.getString(cursor
							.getColumnIndex(MediaStore.MediaColumns.DATA));
					title = cursor.getString(cursor
							.getColumnIndex(MediaStore.MediaColumns.TITLE));
					displayName = cursor
							.getString(cursor
									.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME));
					dateAdded = cursor
							.getLong(cursor
									.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED));
					originalData = new OriginalData(id, uri, data, title,
							displayName, dateAdded);
					mListFiles.add(originalData);
				}
			}
		} catch (Exception ex) {
			Log.e(TAG, "getImageList Exception: " + ex.getMessage(), ex);
		} finally {
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
		}
	}

	/**
	 * This is an utility method used to show columns and values from a table.
	 * 
	 * @param cr
	 *            The application ContentResolver
	 * @param uri
	 *            The database URI path.
	 */
	private void doQuery(ContentResolver cr, Uri uri) {
		Cursor cursor = cr.query(uri, null, null, null, null);
		if (cursor != null) {
			cursor.moveToFirst();
			int rows = cursor.getCount();
			int cols = cursor.getColumnCount();
			int i, j;
			String rowVal;
			Log.i(TAG, uri.getPath());
			for (i = 0; i < rows; i++) {
				rowVal = "row[" + i + "]:";
				for (j = 0; j < cols; j++) {
					if (j > 0) {
						rowVal += ", ";
					}
					try {
						rowVal += cursor.getColumnName(j) + ": "
								+ cursor.getString(j);
					} catch (Exception e) {
						Log.e(TAG, "[" + j + "]:" + e.getMessage());
					}
				}
				Log.i(TAG, rowVal);
				cursor.moveToNext();
			}
			if (!cursor.isClosed()) {
				cursor.close();
			}
		}
	}
}

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
package ro.ciubex.dscautorename.receiver;

import ro.ciubex.dscautorename.DSCApplication;
import ro.ciubex.dscautorename.task.RenameFileAsyncTask;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

/**
 * Define a receiver when is take picture.
 * 
 * @author Claudiu Ciobotariu
 * 
 */
public class CameraEventReceiver extends BroadcastReceiver {
	private DSCApplication mApplication;

	/**
	 * This method is called when the BroadcastReceiver is receiving an Intent
	 * broadcast.
	 * 
	 * @param context
	 *            The Context in which the receiver is running.
	 * @param intent
	 *            The Intent being received.
	 */
	@Override
	public void onReceive(final Context context, final Intent intent) {
		Context appCtx = context.getApplicationContext();
		if (appCtx instanceof DSCApplication) {
			mApplication = (DSCApplication) appCtx;
		}
		if (mApplication != null && mApplication.isAutoRenameEnabled()) {
			if (!mApplication.isRenameFileTaskBusy()) {
				mApplication.setRenameFileTaskBusy(true);
				long delayMillis = mApplication.getRenameServiceStartDelay() * 1000;
				// wait couple of seconds to run the thread.
				new Handler().postDelayed(new Runnable() {

					@Override
					public void run() {
						new RenameFileAsyncTask(mApplication).execute();
					}
				}, delayMillis);
			}
		}
	}

}
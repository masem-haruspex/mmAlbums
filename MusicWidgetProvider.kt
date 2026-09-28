package com.mlib.albums

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class MusicWidgetProvider : AppWidgetProvider() {

	// triggers on start and periodic refresh
	override fun onUpdate(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray
	) {
		val intent = Intent(context, MusicService::class.java).apply {
			action = MusicService.ACTION_UPDATE_WIDGET
		}
		context.startService(intent)
	}

	// triggers on widget resize
	override fun onAppWidgetOptionsChanged(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetId: Int,
		newOptions: Bundle?
	) {
		val intent = Intent(context, MusicService::class.java).apply {
			action = MusicService.ACTION_UPDATE_WIDGET
		}
		context.startService(intent)
	}

	// triggers when widget is created
	override fun onEnabled(context: Context) {
		super.onEnabled(context)
		val intent = Intent(context, MusicService::class.java)
		context.startService(intent)
	}
}

package com.concavenp.nanodegree.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.concavenp.nanodegree.shared.SharedUtility;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Created by dave on 9/7/2016.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {

    private static final String TAG = "SunshineWFService";

    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for normal (not ambient and not mute) mode. We update twice
     * a second to blink the colons.
     */
    private static final long NORMAL_UPDATE_RATE_MS = 500;

    /**
     * Update rate in milliseconds for mute mode. We update every minute, like in ambient mode.
     */
    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    @Override
    public Engine onCreateEngine() {

        return new Engine();

    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener, MessageApi.MessageListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        static final String COLON_STRING = ":";

        /** Alpha value for drawing time when in mute mode. */
        static final int MUTE_ALPHA = 100;

        /** Alpha value for drawing time when not in mute mode. */
        static final int NORMAL_ALPHA = 255;

        static final int MSG_UPDATE_TIME = 0;

        /** How often {@link #mUpdateTimeHandler} ticks in milliseconds. */
        long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

        /** Handler to update the time periodically in interactive mode. */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                          //  Log.v(TAG, "updating time");
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = mInteractiveUpdateRateMs - (timeMs % mInteractiveUpdateRateMs);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        /**
         * Handles time zone and locale changes.
         */
        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };

        /**
         * Unregistering an unregistered receiver throws an exception. Keep track of the
         * registration state to prevent that.
         */
        boolean mRegisteredReceiver = false;

        Paint mBackgroundPaint;
        Paint mDatePaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mAmPmPaint;
        Paint mColonPaint;
        float mColonWidth;
        boolean mMute;

        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;

        int mWeatherId = 0;
        String mHighTemp;
        String mLowTemp;
        String mWeatherDescription;

        boolean mShouldDrawColons;
        float mXOffset;
        float mYOffset;
        float mLineHeight;
        String mAmString;
        String mPmString;
        int mInteractiveBackgroundColor = SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND;
        int mInteractiveHourDigitsColor = SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS;
        int mInteractiveMinuteDigitsColor = SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS;
        int mInteractiveSecondDigitsColor = SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {

            Log.d(TAG, "onCreate");

            super.onCreate(holder);

            setWatchFaceStyle(
                    new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                            .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                            .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                            .setShowSystemUiTime(false)
                            .build());
            Resources resources = SunshineWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);
            mAmString = resources.getString(R.string.digital_am);
            mPmString = resources.getString(R.string.digital_pm);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mInteractiveBackgroundColor);
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_date));
            mHourPaint = createTextPaint(mInteractiveHourDigitsColor, BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(mInteractiveMinuteDigitsColor);
            mSecondPaint = createTextPaint(mInteractiveSecondDigitsColor);
            mAmPmPaint = createTextPaint(resources.getColor(R.color.digital_am_pm));
            mColonPaint = createTextPaint(resources.getColor(R.color.digital_colons));

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            initFormats();

        }

        @Override
        public void onDestroy() {

            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();

        }

        private Paint createTextPaint(int defaultInteractiveColor) {

            return createTextPaint(defaultInteractiveColor, NORMAL_TYPEFACE);

        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {

            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;

        }

        @Override
        public void onVisibilityChanged(boolean visible) {

            Log.d(TAG, "onVisibilityChanged: " + visible);

            super.onVisibilityChanged(visible);

            if (visible) {

                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();

            } else {

                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {

                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();

                }

            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();

        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
            mDateFormat = DateFormat.getDateFormat(SunshineWatchFaceService.this);
            mDateFormat.setCalendar(mCalendar);
        }

        private void registerReceiver() {

            if (mRegisteredReceiver) {
                return;
            }

            mRegisteredReceiver = true;

            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mReceiver, filter);

        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float amPmSize = resources.getDimension(isRound ? R.dimen.digital_am_pm_size_round : R.dimen.digital_am_pm_size);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mSecondPaint.setTextSize(textSize);
            mAmPmPaint.setTextSize(amPmSize);
            mColonPaint.setTextSize(textSize);

            mColonWidth = mColonPaint.measureText(COLON_STRING);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {

            super.onPropertiesChanged(properties);

            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mHourPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

            Log.d(TAG, "onPropertiesChanged: burn-in protection = " + burnInProtection + ", low-bit ambient = " + mLowBitAmbient);

        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {

            super.onAmbientModeChanged(inAmbientMode);

            Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);

            adjustPaintColorToCurrentMode(mBackgroundPaint, mInteractiveBackgroundColor, SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);
            adjustPaintColorToCurrentMode(mHourPaint, mInteractiveHourDigitsColor, SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS);
            adjustPaintColorToCurrentMode(mMinutePaint, mInteractiveMinuteDigitsColor, SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);

            // Actually, the seconds are not rendered in the ambient mode, so we could pass just any
            // value as ambientColor here.
            adjustPaintColorToCurrentMode(mSecondPaint, mInteractiveSecondDigitsColor, SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS);

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mDatePaint.setAntiAlias(antiAlias);
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mSecondPaint.setAntiAlias(antiAlias);
                mAmPmPaint.setAntiAlias(antiAlias);
                mColonPaint.setAntiAlias(antiAlias);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        private void adjustPaintColorToCurrentMode(Paint paint, int interactiveColor, int ambientColor) {
            paint.setColor(isInAmbientMode() ? ambientColor : interactiveColor);
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
                Log.d(TAG, "onInterruptionFilterChanged: " + interruptionFilter);
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE;
            // We only need to update once a minute in mute mode.
            setInteractiveUpdateRateMs(inMuteMode ? MUTE_UPDATE_RATE_MS : NORMAL_UPDATE_RATE_MS);

            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                int alpha = inMuteMode ? MUTE_ALPHA : NORMAL_ALPHA;
                mDatePaint.setAlpha(alpha);
                mHourPaint.setAlpha(alpha);
                mMinutePaint.setAlpha(alpha);
                mColonPaint.setAlpha(alpha);
                mAmPmPaint.setAlpha(alpha);
                invalidate();
            }
        }

        public void setInteractiveUpdateRateMs(long updateRateMs) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return;
            }
            mInteractiveUpdateRateMs = updateRateMs;

            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerBeRunning()) {
                updateTimer();
            }
        }

        private void updatePaintIfInteractive(Paint paint, int interactiveColor) {
            if (!isInAmbientMode() && paint != null) {
                paint.setColor(interactiveColor);
            }
        }

        private void setInteractiveBackgroundColor(int color) {
            mInteractiveBackgroundColor = color;
            updatePaintIfInteractive(mBackgroundPaint, color);
        }

        private void setInteractiveHourDigitsColor(int color) {
            mInteractiveHourDigitsColor = color;
            updatePaintIfInteractive(mHourPaint, color);
        }

        private void setInteractiveMinuteDigitsColor(int color) {
            mInteractiveMinuteDigitsColor = color;
            updatePaintIfInteractive(mMinutePaint, color);
        }

        private void setInteractiveSecondDigitsColor(int color) {
            mInteractiveSecondDigitsColor = color;
            updatePaintIfInteractive(mSecondPaint, color);
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        private String getAmPmString(int amPm) {
            return amPm == Calendar.AM ? mAmString : mPmString;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchFaceService.this);

            // Show colons for the first half of each second so the colons blink on when the time updates.
            mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;

            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw the hours.
            float x = mXOffset;
            String hourString;
            if (is24Hour) {

                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));

            } else {

                int hour = mCalendar.get(Calendar.HOUR);

                if (hour == 0) {

                    hour = 12;

                }

                hourString = String.valueOf(hour);

            }

            canvas.drawText(hourString, x, mYOffset, mHourPaint);
            x += mHourPaint.measureText(hourString);

            // In ambient and mute modes, always draw the first colon. Otherwise, draw the
            // first colon for the first half of each second.
            if (isInAmbientMode() || mMute || mShouldDrawColons) {
                canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
            }
            x += mColonWidth;

            // Draw the minutes.
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteString, x, mYOffset, mMinutePaint);
            x += mMinutePaint.measureText(minuteString);

            // In unmuted interactive mode, draw a second blinking colon followed by the seconds.
            // Otherwise, if we're in 12-hour mode, draw AM/PM
            if (!isInAmbientMode() && !mMute) {
                if (mShouldDrawColons) {
                    canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
                }
                x += mColonWidth;
                canvas.drawText(formatTwoDigitNumber( mCalendar.get(Calendar.SECOND)), x, mYOffset, mSecondPaint);
            } else if (!is24Hour) {
                x += mColonWidth;
                canvas.drawText(getAmPmString( mCalendar.get(Calendar.AM_PM)), x, mYOffset, mAmPmPaint);
            }

            // Day of week
            String formattedDayOfWeek = mDayOfWeekFormat.format(mDate);
            canvas.drawText(formattedDayOfWeek, mXOffset, mYOffset + mLineHeight, mDatePaint);

            // Date
            String formattedDate = mDateFormat.format(mDate);
            canvas.drawText(formattedDate, mXOffset, mYOffset + mLineHeight * 2, mDatePaint);

            // Determine where the place the temperature measurements on the watch face
            float xDOW = mDatePaint.measureText(formattedDayOfWeek);
            float xDate = mDatePaint.measureText(formattedDate);
            float xLocation = mXOffset + mDatePaint.measureText(" ") + ((xDOW > xDate) ? xDOW : xDate);

            // High Temp
            String formattedHighTemp = "High: " + mHighTemp;
            canvas.drawText(formattedHighTemp, xLocation, mYOffset + mLineHeight, mDatePaint);

            // Low Temp
            String formattedLowTemp = "Low: " + mLowTemp;
            canvas.drawText(formattedLowTemp, xLocation, mYOffset + (mLineHeight * 2), mDatePaint);

            // Weather Icon
            if (mWeatherId != 0) {

                // Extract the bitmaps from the "shared" module resources and scale them down.
                Bitmap weatherBitmap = SunshineWatchFaceUtil.decodeSampledBitmapFromResource(
                        getResources(),
                        SharedUtility.getArtResourceForWeatherCondition(mWeatherId),
                        getResources().getInteger(R.integer.scaled_square_size),
                        getResources().getInteger(R.integer.scaled_square_size));

                if (isInAmbientMode())  {
                    weatherBitmap = SunshineWatchFaceUtil.toGrayscale(weatherBitmap);
                }

                // Draw the image
                canvas.drawBitmap(weatherBitmap,xLocation/2,mYOffset + (mLineHeight*2),mDatePaint);

            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
                Log.d(TAG, "updateTimer");
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private void updateWeatherDataItemAndUiOnStartup() {

            SunshineWatchFaceUtil.fetchWeatherDataMap(mGoogleApiClient, new SunshineWatchFaceUtil.FetchWeatherDataMapCallback() {

                        @Override
                        public void onWeatherDataMapFetched(DataMap weatherState) {

                            // If the DataItem hasn't been created yet or some keys are missing, use the default values.
                            setDefaultValuesForMissingConfigKeys(weatherState);

                            SunshineWatchFaceUtil.putWeatherDataItem(mGoogleApiClient, weatherState);

                            updateUiForWeatherDataMap(weatherState);

                        }

                    }

            );

        }

        private void setDefaultValuesForMissingConfigKeys(DataMap weatherState) {

            addIntKeyIfMissing(weatherState, SunshineWatchFaceUtil.KEY_BACKGROUND_COLOR, SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);
            addIntKeyIfMissing(weatherState, SunshineWatchFaceUtil.KEY_HOURS_COLOR, SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS);
            addIntKeyIfMissing(weatherState, SunshineWatchFaceUtil.KEY_MINUTES_COLOR, SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);
            addIntKeyIfMissing(weatherState, SunshineWatchFaceUtil.KEY_SECONDS_COLOR, SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS);

        }

        private void addIntKeyIfMissing(DataMap config, String key, int color) {

            if (!config.containsKey(key)) {

                config.putInt(key, color);

            }

        }

        @Override // WearableListenerService
        public void onMessageReceived(MessageEvent messageEvent) {

            Log.d(TAG, "onMessageReceived: " + messageEvent);

            if (!messageEvent.getPath().equals(SunshineWatchFaceUtil.PATH_WITH_FEATURE)) {

                return;

            }
            byte[] rawData = messageEvent.getData();
            // It's allowed that the message carries only some of the keys used in the config DataItem
            // and skips the ones that we don't want to change.
            DataMap weatherKeysToOverwrite = DataMap.fromByteArray(rawData);

            Log.d(TAG, "Received watch face weather message: " + weatherKeysToOverwrite);

            SunshineWatchFaceUtil.overwriteKeysInWeatherDataMap(mGoogleApiClient, weatherKeysToOverwrite);

        }

        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {

            for (DataEvent dataEvent : dataEvents) {

                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {

                    continue;

                }

                DataItem dataItem = dataEvent.getDataItem();

                // Verify this NOT our Weather data update from the mobile Sunshine app
                if (!dataItem.getUri().getPath().equals( SunshineWatchFaceUtil.PATH_WITH_FEATURE)) {

                    // This is not our data, ignore it
                    continue;

                }

                // Extract the data
                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap weatherState = dataMapItem.getDataMap();

                Log.d(TAG, "Weather DataItem updated:" + weatherState);

                updateUiForWeatherDataMap(weatherState);

            }

        }

        private void updateUiForWeatherDataMap(final DataMap weatherState) {

            boolean uiUpdated = false;

            for (String key : weatherState.keySet()) {

                // Inspect the key-pair value and apply the data appropriately
                if (updateUiForKey(key, weatherState )) {

                    uiUpdated = true;

                }

            }

            // Only redraw the screen if something changed
            if (uiUpdated) {

                invalidate();

            }

        }

        /**
         * Updates the color of a UI item according to the given {@code key}. Does nothing if
         * {@code key} isn't recognized.
         *
         * @return whether UI has been updated
         */
        private boolean updateUiForKey(String key, final DataMap weatherState) {

            if (key.equals(SunshineWatchFaceUtil.KEY_BACKGROUND_COLOR)) {

                setInteractiveBackgroundColor(weatherState.getInt(key));

            } else if (key.equals(SunshineWatchFaceUtil.KEY_HOURS_COLOR)) {

                setInteractiveHourDigitsColor(weatherState.getInt(key));

            } else if (key.equals(SunshineWatchFaceUtil.KEY_MINUTES_COLOR)) {

                setInteractiveMinuteDigitsColor(weatherState.getInt(key));

            } else if (key.equals(SunshineWatchFaceUtil.KEY_SECONDS_COLOR)) {

                setInteractiveSecondDigitsColor(weatherState.getInt(key));

            } else if (key.equals(SunshineWatchFaceUtil.KEY_MAX_TEMP)) {

                mHighTemp = weatherState.getString(key);

            } else if (key.equals(SunshineWatchFaceUtil.KEY_MIN_TEMP)) {

                mLowTemp = weatherState.getString(key);

            } else if (key.equals(SunshineWatchFaceUtil.KEY_WEATHER_ID)) {

                mWeatherId = weatherState.getInt(key);

            } else if (key.equals(SunshineWatchFaceUtil.KEY_SHORT_DESC)) {

                // TODO: unused for now - could do something cool with this

            } else {

                Log.w(TAG, "Ignoring unknown config key: " + key);
                return false;

            }

            return true;
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {

            Log.d(TAG, "onConnected: " + connectionHint);

            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);

            updateWeatherDataItemAndUiOnStartup();

        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {

            Log.d(TAG, "onConnectionSuspended: " + cause);

        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {

            Log.d(TAG, "onConnectionFailed: " + result);

        }

    }

}

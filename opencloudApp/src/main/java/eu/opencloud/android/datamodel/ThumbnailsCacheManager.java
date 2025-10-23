/**
 * openCloud Android client application
 *
 * @author Tobias Kaminsky
 * @author David A. Velasco
 * @author Christian Schabesberger
 * Copyright (C) 2020 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.datamodel;

import android.accounts.Account;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.ImageView;
import android.util.LruCache;

import androidx.core.content.ContextCompat;
import eu.opencloud.android.MainApp;
import eu.opencloud.android.R;
import eu.opencloud.android.domain.files.model.OCFile;
import eu.opencloud.android.domain.files.usecases.DisableThumbnailsForFileUseCase;
import eu.opencloud.android.domain.files.usecases.GetWebDavUrlForSpaceUseCase;
import eu.opencloud.android.domain.spaces.model.SpaceSpecial;
import eu.opencloud.android.lib.common.OpenCloudAccount;
import eu.opencloud.android.lib.common.OpenCloudClient;
import eu.opencloud.android.lib.common.SingleSessionManager;
import eu.opencloud.android.lib.common.accounts.AccountUtils;
import eu.opencloud.android.lib.common.http.HttpConstants;
import eu.opencloud.android.lib.common.http.methods.nonwebdav.GetMethod;
import eu.opencloud.android.ui.adapter.DiskLruImageCache;
import eu.opencloud.android.utils.BitmapUtils;
import kotlin.Lazy;
import timber.log.Timber;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Locale;

import static org.koin.java.KoinJavaComponent.inject;

/**
 * Manager for concurrent access to thumbnails cache.
 */
public class ThumbnailsCacheManager {

    private static final String CACHE_FOLDER = "thumbnailCache";

    private static final Object mThumbnailsDiskCacheLock = new Object();
    private static final Object mMemoryCacheLock = new Object();
    private static DiskLruImageCache mThumbnailCache = null;
    private static boolean mThumbnailCacheStarting = true;
    private static final LruCache<String, Bitmap> mMemoryCache = createMemoryCache();

    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB
    private static final CompressFormat mCompressFormat = CompressFormat.JPEG;
    private static final int mCompressQuality = 70;

    private static final String PREVIEW_URI = "%s%s?x=%d&y=%d&c=%s&preview=1";
    private static final String SPACE_SPECIAL_URI = "%s?scalingup=0&a=1&x=%d&y=%d&c=%s&preview=1";

    public static Bitmap mDefaultImg =
            BitmapFactory.decodeResource(
                    MainApp.Companion.getAppContext().getResources(),
                    R.drawable.file_image
            );

    public static class InitDiskCacheTask extends AsyncTask<File, Void, Void> {

        @Override
        protected Void doInBackground(File... params) {
            synchronized (mThumbnailsDiskCacheLock) {
                mThumbnailCacheStarting = true;

                if (mThumbnailCache == null) {
                    try {
                        // Check if media is mounted or storage is built-in, if so,
                        // try and use external cache dir; otherwise use internal cache dir
                        File cacheBaseDir = MainApp.Companion.getAppContext().getExternalCacheDir();
                        if (cacheBaseDir == null) {
                            cacheBaseDir = MainApp.Companion.getAppContext().getCacheDir();
                        }
                        if (cacheBaseDir != null) {
                            final File diskCacheDir = new File(cacheBaseDir, CACHE_FOLDER);
                            Timber.d("create dir: %s", diskCacheDir.getAbsolutePath());
                            mThumbnailCache = new DiskLruImageCache(
                                    diskCacheDir,
                                    DISK_CACHE_SIZE,
                                    mCompressFormat,
                                    mCompressQuality
                            );
                        } else {
                            Timber.w("Thumbnail cache could not be opened: no cache directory available");
                            mThumbnailCache = null;
                        }
                    } catch (Exception e) {
                        Timber.e(e, "Thumbnail cache could not be opened ");
                        mThumbnailCache = null;
                    }
                }
                mThumbnailCacheStarting = false; // Finished initialization
                mThumbnailsDiskCacheLock.notifyAll(); // Wake any waiting threads
            }
            return null;
        }
    }

    public static void addBitmapToCache(String key, Bitmap bitmap) {
        if (key == null || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        addBitmapToMemoryCache(key, bitmap);
        synchronized (mThumbnailsDiskCacheLock) {
            if (mThumbnailCache != null) {
                mThumbnailCache.put(key, bitmap);
            }
        }
    }

    public static void removeBitmapFromCache(String key) {
        removeBitmapFromMemoryCache(key);
        synchronized (mThumbnailsDiskCacheLock) {
            if (mThumbnailCache != null) {
                mThumbnailCache.removeKey(key);
            }
        }
    }

    public static Bitmap getBitmapFromDiskCache(String key) {
        Bitmap memoryBitmap = getBitmapFromMemoryCache(key);
        if (memoryBitmap != null) {
            if (!memoryBitmap.isRecycled()) {
                return memoryBitmap;
            } else {
                removeBitmapFromMemoryCache(key);
            }
        }
        synchronized (mThumbnailsDiskCacheLock) {
            // Wait while disk cache is started from background thread
            while (mThumbnailCacheStarting) {
                try {
                    mThumbnailsDiskCacheLock.wait();
                } catch (InterruptedException e) {
                    Timber.e(e, "Wait in mThumbnailsDiskCacheLock was interrupted");
                }
            }
            if (mThumbnailCache != null) {
                Bitmap diskBitmap = mThumbnailCache.getBitmap(key);
                if (diskBitmap != null && !diskBitmap.isRecycled()) {
                    addBitmapToMemoryCache(key, diskBitmap);
                }
                return diskBitmap;
            }
        }
        return null;
    }

    public static Bitmap getBitmapFromDiskCache(OCFile file) {
        String cacheKey = getCacheKeyForFile(file);
        if (cacheKey == null) {
            return null;
        }
        return getBitmapFromDiskCache(cacheKey);
    }

    private static LruCache<String, Bitmap> createMemoryCache() {
        final int maxMemoryInKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSizeInKb = Math.max(maxMemoryInKb / 8, 1024);
        return new LruCache<String, Bitmap>(cacheSizeInKb) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                if (bitmap == null) {
                    return 0;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    return bitmap.getAllocationByteCount() / 1024;
                } else {
                    return bitmap.getByteCount() / 1024;
                }
            }
        };
    }

    private static Bitmap getBitmapFromMemoryCache(String key) {
        if (key == null) {
            return null;
        }
        synchronized (mMemoryCacheLock) {
            return mMemoryCache.get(key);
        }
    }

    private static void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (key == null || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        synchronized (mMemoryCacheLock) {
            mMemoryCache.put(key, bitmap);
        }
    }

    private static void removeBitmapFromMemoryCache(String key) {
        if (key == null) {
            return;
        }
        synchronized (mMemoryCacheLock) {
            mMemoryCache.remove(key);
        }
    }

    private static void disableThumbnailsForFile(OCFile file) {
        if (file == null) {
            return;
        }
        Long fileId = file.getId();
        if (fileId == null) {
            Timber.w("Unable to disable thumbnails for file without id: %s", file.getRemotePath());
            return;
        }
        Lazy<DisableThumbnailsForFileUseCase> disableThumbnailsForFileUseCaseLazy =
                inject(DisableThumbnailsForFileUseCase.class);
        disableThumbnailsForFileUseCaseLazy.getValue()
                .invoke(new DisableThumbnailsForFileUseCase.Params(fileId));
    }

    private static String getCacheKeyForFile(OCFile file) {
        if (file == null) {
            return null;
        }
        String remoteId = file.getRemoteId();
        if (remoteId != null && !remoteId.isEmpty()) {
            return remoteId;
        }
        String remotePath = file.getRemotePath();
        if (remotePath == null || remotePath.isEmpty()) {
            return null;
        }
        StringBuilder cacheKeyBuilder = new StringBuilder();
        if (file.getOwner() != null) {
            cacheKeyBuilder.append(file.getOwner());
        }
        cacheKeyBuilder.append('|');
        if (file.getSpaceId() != null) {
            cacheKeyBuilder.append(file.getSpaceId());
        }
        cacheKeyBuilder.append('|');
        cacheKeyBuilder.append(remotePath);
        return cacheKeyBuilder.toString();
    }

    public static class ThumbnailGenerationTask extends AsyncTask<Object, Void, Bitmap> {
        private final WeakReference<ImageView> mImageViewReference;
        private final Account mAccount;
        private Object mFile;
        private FileDataStorageManager mStorageManager;
        private OpenCloudClient mClient;

        public ThumbnailGenerationTask(ImageView imageView, Account account) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            mImageViewReference = new WeakReference<>(imageView);
            mAccount = account;
            mClient = null;
        }

        public ThumbnailGenerationTask(ImageView imageView) {
            this(imageView, null);
        }

        @Override
        protected Bitmap doInBackground(Object... params) {
            Bitmap thumbnail = null;

            try {
                if (mAccount != null) {
                    OpenCloudAccount ocAccount = new OpenCloudAccount(
                            mAccount,
                            MainApp.Companion.getAppContext()
                    );
                    mClient = SingleSessionManager.getDefaultSingleton().
                            getClientFor(ocAccount, MainApp.Companion.getAppContext());
                } else {
                    mClient = null;
                }

                mFile = params[0];

                if (mFile instanceof OCFile) {
                    thumbnail = doOCFileInBackground();
                } else if (mFile instanceof File) {
                    thumbnail = doFileInBackground();
                } else if (mFile instanceof SpaceSpecial) {
                    thumbnail = doSpaceImageInBackground();
                    //} else {  do nothing
                }

            } catch (Throwable t) {
                // the app should never break due to a problem with thumbnails
                Timber.e(t, "Generation of thumbnail for " + mFile + " failed");
                if (t instanceof OutOfMemoryError) {
                    System.gc();
                }
            }

            return thumbnail;
        }

        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                final ImageView imageView = mImageViewReference.get();
                final ThumbnailGenerationTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
                if (this == bitmapWorkerTask) {
                    String tagId = "";
                    if (mFile instanceof OCFile) {
                        tagId = String.valueOf(((OCFile) mFile).getId());
                    } else if (mFile instanceof File) {
                        tagId = String.valueOf(mFile.hashCode());
                    } else if (mFile instanceof SpaceSpecial) {
                        tagId = ((SpaceSpecial) mFile).getId();
                    }
                    if (String.valueOf(imageView.getTag()).equals(tagId)) {
                        imageView.setImageBitmap(bitmap);
                    }
                }
            }
        }

        /**
         * Add thumbnail to cache
         *
         * @param imageKey: thumb key
         * @param bitmap:   image for extracting thumbnail
         * @param path:     image path
         * @param px:       thumbnail dp
         * @return Bitmap
         */
        private Bitmap addThumbnailToCache(String imageKey, Bitmap bitmap, String path, int px) {

            Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap, px, px);

            // Rotate image, obeying exif tag
            thumbnail = BitmapUtils.rotateImage(thumbnail, path);

            // Add thumbnail to cache
            addBitmapToCache(imageKey, thumbnail);

            return thumbnail;
        }

        /**
         * Converts size of file icon from dp to pixel
         *
         * @return int
         */
        private int getThumbnailDimension() {
            // Converts dp to pixel
            Resources r = MainApp.Companion.getAppContext().getResources();
            return Math.round(r.getDimension(R.dimen.file_icon_size_grid));
        }

        private String getPreviewUrl(OCFile ocFile) {
            if (mClient == null || mAccount == null) {
                return null;
            }
            String baseUrl = mClient.getBaseUri() + "/remote.php/dav/files/" + AccountUtils.getUserId(mAccount, MainApp.Companion.getAppContext());

            if (ocFile.getSpaceId() != null) {
                Lazy<GetWebDavUrlForSpaceUseCase> getWebDavUrlForSpaceUseCaseLazy = inject(GetWebDavUrlForSpaceUseCase.class);
                baseUrl = getWebDavUrlForSpaceUseCaseLazy.getValue().invoke(
                        new GetWebDavUrlForSpaceUseCase.Params(ocFile.getOwner(), ocFile.getSpaceId())
                );

            }
            if (baseUrl == null) {
                return null;
            }
            return String.format(Locale.ROOT,
                    PREVIEW_URI,
                    baseUrl,
                    Uri.encode(ocFile.getRemotePath(), "/"),
                    getThumbnailDimension(),
                    getThumbnailDimension(),
                    ocFile.getEtag());
        }

        private Bitmap doOCFileInBackground() {
            OCFile file = (OCFile) mFile;

            final String imageKey = getCacheKeyForFile(file);
            if (imageKey == null) {
                return null;
            }

            // Check disk cache in background thread
            Bitmap thumbnail = getBitmapFromDiskCache(imageKey);

            // Not found in disk cache
            if (thumbnail == null || file.getNeedsToUpdateThumbnail()) {

                int px = getThumbnailDimension();

                // Download thumbnail from server
                if (mClient != null) {
                    GetMethod get;
                    try {
                        String uri = getPreviewUrl(file);
                        if (uri == null) {
                            return thumbnail;
                        }
                        Timber.d("URI: %s", uri);
                        get = new GetMethod(new URL(uri));
                        int status = mClient.executeHttpMethod(get);
                        if (status == HttpConstants.HTTP_OK) {
                            InputStream inputStream = get.getResponseBodyAsStream();
                            try {
                                if (inputStream != null) {
                                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                    if (bitmap != null) {
                                        thumbnail = ThumbnailUtils.extractThumbnail(bitmap, px, px);
                                    }
                                    if (thumbnail != null && file.getMimeType().equalsIgnoreCase("image/png")) {
                                        thumbnail = handlePNG(thumbnail, px);
                                    }
                                    if (thumbnail != null) {
                                        addBitmapToCache(imageKey, thumbnail);
                                        file.setNeedsToUpdateThumbnail(false);
                                        disableThumbnailsForFile(file);
                                    }
                                }
                            } finally {
                                mClient.exhaustResponse(inputStream);
                            }
                        } else {
                            InputStream responseStream = get.getResponseBodyAsStream();
                            try {
                                if (status == HttpConstants.HTTP_NOT_FOUND) {
                                    file.setNeedsToUpdateThumbnail(false);
                                    disableThumbnailsForFile(file);
                                }
                            } finally {
                                mClient.exhaustResponse(responseStream);
                            }
                        }
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
            }

            return thumbnail;

        }

        private Bitmap handlePNG(Bitmap bitmap, int px) {
            Bitmap resultBitmap = Bitmap.createBitmap(px,
                    px,
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(resultBitmap);

            c.drawColor(ContextCompat.getColor(MainApp.Companion.getAppContext(), R.color.background_color));
            c.drawBitmap(bitmap, 0, 0, null);

            return resultBitmap;
        }

        private Bitmap doFileInBackground() {
            File file = (File) mFile;

            final String imageKey = String.valueOf(file.hashCode());

            // Check disk cache in background thread
            Bitmap thumbnail = getBitmapFromDiskCache(imageKey);

            // Not found in disk cache
            if (thumbnail == null) {

                int px = getThumbnailDimension();

                Bitmap bitmap = BitmapUtils.decodeSampledBitmapFromFile(
                        file.getAbsolutePath(), px, px);

                if (bitmap != null) {
                    thumbnail = addThumbnailToCache(imageKey, bitmap, file.getPath(), px);
                }
            }
            return thumbnail;
        }

        private String getSpaceSpecialUri(SpaceSpecial spaceSpecial) {
            // Converts dp to pixel
            Resources r = MainApp.Companion.getAppContext().getResources();
            Integer spacesThumbnailSize = Math.round(r.getDimension(R.dimen.spaces_thumbnail_height)) * 2;
            return String.format(Locale.ROOT,
                    SPACE_SPECIAL_URI,
                    spaceSpecial.getWebDavUrl(),
                    spacesThumbnailSize,
                    spacesThumbnailSize,
                    spaceSpecial.getETag());
        }

        private Bitmap doSpaceImageInBackground() {
            SpaceSpecial spaceSpecial = (SpaceSpecial) mFile;

            final String imageKey = spaceSpecial.getId();

            // Check disk cache in background thread
            Bitmap thumbnail = getBitmapFromDiskCache(imageKey);

            // Not found in disk cache
            if (thumbnail == null) {
                int px = getThumbnailDimension();

                // Download thumbnail from server
                if (mClient != null) {
                    GetMethod get;
                    try {
                        String uri = getSpaceSpecialUri(spaceSpecial);
                        Timber.d("URI: %s", uri);
                        get = new GetMethod(new URL(uri));
                        int status = mClient.executeHttpMethod(get);
                        if (status == HttpConstants.HTTP_OK) {
                            InputStream inputStream = get.getResponseBodyAsStream();
                            try {
                                if (inputStream != null) {
                                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                    if (bitmap != null) {
                                        thumbnail = ThumbnailUtils.extractThumbnail(bitmap, px, px);
                                    }

                                    // Handle PNG
                                    if (thumbnail != null
                                            && spaceSpecial.getFile() != null
                                            && spaceSpecial.getFile().getMimeType().equalsIgnoreCase("image/png")) {
                                        thumbnail = handlePNG(thumbnail, px);
                                    }

                                    // Add thumbnail to cache
                                    if (thumbnail != null) {
                                        addBitmapToCache(imageKey, thumbnail);
                                    }
                                }
                            } finally {
                                mClient.exhaustResponse(inputStream);
                            }
                        } else {
                            mClient.exhaustResponse(get.getResponseBodyAsStream());
                        }
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
            }

            return thumbnail;

        }
    }

    public static boolean cancelPotentialThumbnailWork(Object file, ImageView imageView) {
        final ThumbnailGenerationTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Object bitmapData = bitmapWorkerTask.mFile;
            // If bitmapData is not yet set or it differs from the new data
            if (bitmapData == null || bitmapData != file) {
                // Cancel previous task
                bitmapWorkerTask.cancel(true);
                Timber.v("Cancelled generation of thumbnail for a reused imageView");
            } else {
                // The same work is already in progress
                return false;
            }
        }
        // No task associated with the ImageView, or an existing task was cancelled
        return true;
    }

    public static void executeThumbnailTask(ThumbnailGenerationTask task, Object file) {
        if (task == null || file == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, file);
        } else {
            task.execute(file);
        }
    }

    private static ThumbnailGenerationTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncThumbnailDrawable) {
                final AsyncThumbnailDrawable asyncDrawable = (AsyncThumbnailDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    public static class AsyncThumbnailDrawable extends BitmapDrawable {
        private final WeakReference<ThumbnailGenerationTask> bitmapWorkerTaskReference;

        public AsyncThumbnailDrawable(
                Resources res, Bitmap bitmap, ThumbnailGenerationTask bitmapWorkerTask
        ) {

            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        ThumbnailGenerationTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }
}

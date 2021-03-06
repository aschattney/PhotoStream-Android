/*
 * The MIT License
 *
 * Copyright (c) 2016 Andreas Schattney
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hochschuledarmstadt.photostream_tools;

import android.content.Context;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import hochschuledarmstadt.photostream_tools.model.HttpError;
import hochschuledarmstadt.photostream_tools.model.Photo;
import hochschuledarmstadt.photostream_tools.model.PhotoQueryResult;

class LoadPhotosAsyncTask extends BaseAsyncTask<Void, Void, PhotoQueryResult> {

    private static final String TAG = LoadPhotosAsyncTask.class.getName();
    private final ImageCacher imageCacher;
    private final GetPhotosCallback callback;
    private final HttpGetExecutor executor;
    private final HttpImageLoader imageLoader;

    public LoadPhotosAsyncTask(HttpGetExecutor executor, HttpImageLoader imageLoader, ImageCacher imageCacher, GetPhotosCallback callback) {
        super();
        this.executor = executor;
        this.imageLoader = imageLoader;
        this.imageCacher = imageCacher;
        this.callback = callback;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected PhotoQueryResult doInBackground(Void... params) {
        try {
            return getPhotos();
        } catch (HttpPhotoStreamException e) {
            Logger.log(TAG, LogLevel.ERROR, e.toString());
            final HttpError httpError = e.getHttpError();
            postError(httpError);
        } catch (IOException e) {
            Logger.log(TAG, LogLevel.ERROR, e.toString());
            final HttpError httpError = new HttpError(-1, e.toString());
            postError(httpError);
        }
        return null;
    }

    private PhotoQueryResult getPhotos() throws IOException, HttpPhotoStreamException {
        final HttpResponse httpResponse = executor.execute();
        int statusCode = httpResponse.getStatusCode();
        PhotoQueryResult photoQueryResult = null;
        if (statusCode == HttpURLConnection.HTTP_OK) {
            String jsonResult = httpResponse.getResult();
            Gson gson = new Gson();
            photoQueryResult = gson.fromJson(jsonResult, PhotoQueryResult.class);
            cacheImagesIfNecessary(photoQueryResult);
            jsonResult = gson.toJson(photoQueryResult);
            callback.onNewETag(executor.getEtag(), photoQueryResult.getPage(), jsonResult);

        } else if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            int page = executor.getPage();
            photoQueryResult = callback.onNoNewPhotosAvailable(page);
            if (photoQueryResult != null)
                cacheImagesIfNecessary(photoQueryResult);
        }

        return photoQueryResult;
    }

    private void cacheImagesIfNecessary(PhotoQueryResult photoQueryResult) throws IOException {
        final List<Photo> photos = photoQueryResult.getPhotos();
        List<Photo> uncachedPhotos = new ArrayList<>();
        for (Photo photo : photos) {
            int photoId = photo.getId();
            if (!imageCacher.isCached(photoId)) {
                uncachedPhotos.add(photo);
            } else {
                imageCacher.cacheImage(photo);
            }
        }
        if (uncachedPhotos.size() > 0) {
            imageLoader.execute(uncachedPhotos);
            while (imageLoader.isRunning()) {
                HttpImageLoader.HttpImage httpImage = imageLoader.take();
                if (httpImage != null) {
                    imageCacher.cacheImage(httpImage.getPhoto(), httpImage.getImageData());
                }
            }
            uncachedPhotos.clear();
        }
    }

    @Override
    protected void onPostExecute(PhotoQueryResult result) {
        super.onPostExecute(result);
        if (result != null) {
            callback.onPhotosResult(result);
        }
    }

    @Override
    protected void sendError(HttpError httpError) {
        callback.onPhotosError(httpError);
    }


    interface GetPhotosCallback {
        void onPhotosResult(PhotoQueryResult photoQueryResult);

        void onPhotosError(HttpError httpError);

        void onNewETag(String eTag, int page, String jsonStringPhotoQueryResult);

        PhotoQueryResult onNoNewPhotosAvailable(int page);
    }

}

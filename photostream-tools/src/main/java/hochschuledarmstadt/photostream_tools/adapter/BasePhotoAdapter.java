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

package hochschuledarmstadt.photostream_tools.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import hochschuledarmstadt.photostream_tools.R;
import hochschuledarmstadt.photostream_tools.model.Photo;

/**
 * Mit dieser Klasse können Photos in einer RecyclerView angezeigt werden
 * @param <H> ViewHolder Klasse
 */
public abstract class BasePhotoAdapter<H extends RecyclerView.ViewHolder> extends BaseAdapter<H, Photo> {


    private  ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            5, 25, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(100, true));

    {
        threadPoolExecutor.allowCoreThreadTimeOut(true);
    }

    private static final int FAVORED = -10;
    private static final int UNFAVORED = -11;

    private static final int DEFAULT_CACHE_SIZE_IN_MB = (int) (Runtime.getRuntime().maxMemory() / 1024 / 1024 / 5);

    private List<BitmapLoaderTask> tasks = new ArrayList<>();
    private OnImageLoadedListener listener = new InternalBitmapLoaderListener();

    private BasePhotoAdapter(ArrayList<Photo> photos, int cacheSizeInMegaByte){
        super(photos);
        Log.d(BasePhotoAdapter.class.getName(), String.format("Using %d MB for the lru photo cache", cacheSizeInMegaByte));
    }

    public BasePhotoAdapter(int cacheSizeInMegaByte){
        this(new ArrayList<Photo>(), cacheSizeInMegaByte);
    }

    public BasePhotoAdapter(){
        this(new ArrayList<Photo>(), DEFAULT_CACHE_SIZE_IN_MB);
    }

    /**
     * Liefert das Photo ({@code Photo}) an der Position {@code position} zurück
     * @param position Position in der Liste
     * @return {@code Photo} der Kommentar
     */
    @Override
    public Photo getItemAtPosition(int position) {
        return super.getItemAtPosition(position);
    }

    /**
     * Hängt ein Photo {@code photo} an den <b>Anfang</b> der Liste an
     * @param photo Photo das an den <b>Anfang</b> der Liste hinzugefügt werden soll
     */
    @Override
    public void addAtFront(Photo photo) {
        super.addAtFront(photo);
    }

    /**
     * Hängt ein Photo {@code photo} an das <b>Ende</b> der Liste an
     * @param photo Photo, das an das <b>Ende</b> der Liste hinzugefügt werden soll
     */
    @Override
    public void add(Photo photo) {
        super.add(photo);
    }

    /**
     * Fügt alle Elemente in der Liste {@code photos} an das <b>Ende</b> der Liste an
     * @param photos Liste von Photos, die an das <b>Ende</b> Liste angefügt werden sollen
     */
    @Override
    public void addAll(Collection<? extends Photo> photos) {
        super.addAll(photos);
    }

    /**
     * Ersetzt die aktuelle Liste des Adapters durch eine neue Liste von Photos {@code photos}
     * @param photos die neue Liste von Photos
     */
    @Override
    public void set(Collection<? extends Photo> photos) {
        super.set(photos);
    }

    /**
     * Entfernt ein Photo aus der Liste mit der übergebenen {@code id}
     * @param id id des Photos
     */
    @Override
    public void remove(int id) {
        super.remove(id);
    }

    /**
     * Liefert die Anzahl der Photos in der Liste
     * @return Anzahl der Photos
     */
    @Override
    public int getItemCount() {
        return super.getItemCount();
    }

    @Override
    protected void destroyReferences() {
        super.destroyReferences();
        for (BitmapLoaderTask task : tasks) {
            Log.d(BasePhotoAdapter.class.getSimpleName(), "cancelled task");
            task.cancel(true);
            WeakReference<ImageView> imageViewReference = task.getImageViewReference();
            if (imageViewReference != null && imageViewReference.get() != null)
                imageViewReference.get().setImageBitmap(null);
        }
        tasks.clear();
        listener = null;
        threadPoolExecutor.shutdown();
    }

    /**
     * Stellt die Liste von Photos aus einem Bundle wieder her
     * @param bundle das Bundle, welches die Liste von Photos enthält
     */
    @Override
    public void restoreInstanceState(Bundle bundle) {
        super.restoreInstanceState(bundle);
    }

    /**
     * Aktualisiert ein Photo mit der id {@code photoId} auf den Status <b>favorisiert</b>
     * @param photoId id des Photos
     * @return {@code true}, wenn das Photo innerhalb die Liste vorhanden ist, ansonsten {@code false}
     */
    public boolean favorPhoto(int photoId) {
        return internalFavorOrUnfavorPhoto(photoId, FAVORED);
    }

    /**
     * Aktualisiert ein Photo mit der id {@code photoId} auf den Status <b>nicht favorisiert</b>
     * @param photoId id des Photos
     * @return {@code true}, wenn das Photo innerhalb die Liste vorhanden ist, ansonsten {@code false}
     */
    public boolean unfavorPhoto(int photoId) {
        return internalFavorOrUnfavorPhoto(photoId, UNFAVORED);
    }

    private boolean internalFavorOrUnfavorPhoto(int photoId, int favoriteConstant) {
        int itemCount = getItemCount();
        for (int position = 0; position < itemCount; position++) {
            Photo photo = getItemAtPosition(position);
            if (itemHasEqualId(photoId, photo)) {
                photo.setFavorite(favoriteConstant == FAVORED);
                notifyItemChanged(position);
                return true;
            }
        }
        return false;
    }

    /**
     * Über diese Methode kann die aktuelle Anzahl der
     * Kommentare {@code comment_count} für das {@link Photo}
     * Objekt mit der Id {@code photoId} gesetzt werden
     * @param photoId Die Id zu dem Photo
     * @param comment_count Anzahl der Kommentare zu dem Photo mit der id {@code photoId}
     */
    public void updateCommentCount(int photoId, int comment_count) {
        int itemCount = getItemCount();
        for (int position = 0; position < itemCount; position++) {
            Photo photo = getItemAtPosition(position);
            if (itemHasEqualId(photoId, photo)) {
                try {
                    Field f = photo.getClass().getDeclaredField("commentCount");
                    f.setAccessible(true);
                    f.set(photo, comment_count);
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                notifyItemChanged(position);
            }
        }
    }

    /**
     * Über diese Methode kann ein Photo asynchron geladen werden. Wenn das Photo geladen werden konnte,
     * wird die Methode {@link BasePhotoAdapter#onBitmapLoadedIntoImageView(ImageView)} aufgerufen und die
     * ImageView hier als Parameter mit übergeben.
     * @param viewHolder das ViewHolder Objekt
     * @param imageView die ImageView, in der das geladene Photo angezeigt werden soll
     * @param photo das Photo, das geladen werden soll
     */
    protected void loadBitmapIntoImageViewAsync(H viewHolder, final ImageView imageView, final Photo photo){
        if (imageView.getDrawable() instanceof AsyncDrawable) {
            AsyncDrawable drawable = (AsyncDrawable) imageView.getDrawable();
            Bitmap bitmap = drawable.getBitmap();
            if (bitmap != null && !bitmap.isRecycled())
                bitmap.recycle();
        }
        imageView.setImageBitmap(null);

/*        Integer prevKey = -1;
        try{
            prevKey = Integer.valueOf(imageView.getTag().toString());
        }catch(Exception e){}*/

        if (cancelPotentialWork(photo.getId(), imageView)) {

            /*if (prevKey != -1){
                lruBitmapCache.referenceDecrease(prevKey);
            }*/

            Object tag = viewHolder.itemView.getTag(R.id.should_animate);
            boolean shouldAnimate = tag == null || !tag.equals(Boolean.FALSE);
            if (!shouldAnimate)
                viewHolder.itemView.setTag(R.id.should_animate, Boolean.TRUE);

            BitmapLoaderTask task = new BitmapLoaderTask(imageView, photo.getId(), photo.getImageFile(), listener);
            task.setShouldAnimate(shouldAnimate);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photo.getImageFilePath(), options);
            int imageHeight = options.outHeight;
            int imageWidth = options.outWidth;
            Bitmap placeHolder = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ALPHA_8);
            Canvas canvas = new Canvas(placeHolder);
            canvas.drawColor(Color.BLACK);
            AsyncDrawable asyncDrawable = new AsyncDrawable(imageView.getContext().getResources(), placeHolder, task);
            imageView.setImageDrawable(asyncDrawable);
            task.executeOnExecutor(threadPoolExecutor);
        }
    }

    private boolean cancelPotentialWork(int photoId, ImageView imageView) {
        final BitmapLoaderTask bitmapLoaderTask = BitmapLoaderTask.getBitmapLoaderTaskRefFrom(imageView);
        if (bitmapLoaderTask != null) {
            final int workerTaskPhotoId = bitmapLoaderTask.getPhotoId();
            // If photoId is not yet set or it differs from the new data
            if (photoId != workerTaskPhotoId) {
                // Cancel previous task
                return bitmapLoaderTask.cancel(false);
            } else {
                // The same work is already in progress
                return false;
            }
        }
        // No task associated with the ImageView, or an existing task was cancelled
        return true;
    }

    /**
     * In dieser Methode wird die ImageView zurückgeliefert, nachdem das Photo asynchron geladen und als
     * Bildquelle für die ImageView gesetzt wurde. In dieser Methode könnte theoretisch noch eine
     * kurze Animation durchgeführt werden. Ansonsten kann diese Methode theoretisch leer bleiben.
     * @param imageView
     */
    protected abstract void onBitmapLoadedIntoImageView(ImageView imageView);

    public interface OnItemClickListener<H extends RecyclerView.ViewHolder> extends BaseAdapter.OnItemClickListener<H, Photo>{
        @Override
        void onItemClicked(H viewHolder, View v, Photo photo);
    }

    public interface OnItemLongClickListener<H extends RecyclerView.ViewHolder> extends BaseAdapter.OnItemLongClickListener<H, Photo>{
        @Override
        boolean onItemLongClicked(H viewHolder, View v, Photo photo);
    }

    public interface OnItemTouchListener<H extends RecyclerView.ViewHolder> extends BaseAdapter.OnItemTouchListener<H, Photo>{
        @Override
        boolean onItemTouched(H viewHolder, View v, MotionEvent motionEvent, Photo photo);
    }

    private class InternalBitmapLoaderListener implements OnImageLoadedListener {

        @Override
        public void onTaskStarted(BitmapLoaderTask bitmapLoaderTask) {
            if (!tasks.contains(bitmapLoaderTask))
                tasks.add(bitmapLoaderTask);
        }

        @Override
        public void onTaskFinishedOrCanceled(BitmapLoaderTask bitmapLoaderTask, ImageView imageView) {
            tasks.remove(bitmapLoaderTask);
            if (imageView != null && bitmapLoaderTask.getShouldAnimate())
                onBitmapLoadedIntoImageView(imageView);
        }
    }

}

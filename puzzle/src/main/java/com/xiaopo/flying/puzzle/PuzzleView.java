package com.xiaopo.flying.puzzle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * the puzzle view , the number of puzzle piece due to PuzzleLayout
 *
 * @see PuzzleLayout
 * Created by snowbean on 16-8-9.
 */
public class PuzzleView extends View {
    private static final String TAG = "PhotoLayoutView";

    private enum Mode {
        NONE,
        DRAG,
        ZOOM,
        MOVE
    }

    private Mode mCurrentMode = Mode.NONE;

    private Paint mBitmapPaint;
    private Paint mBorderPaint;

    private RectF mBorderRect;

    private PuzzleLayout mPuzzleLayout;

    private float mBorderWidth = 3;
    private float mExtraSize = 60;

    private float mDownX;
    private float mDownY;

    private float mOldDistance;
    private PointF mMidPoint;

    private List<PuzzlePiece> mPuzzlePieces = new ArrayList<>();

    private Line mHandlingLine;
    private PuzzlePiece mHandlingPiece;
    private List<PuzzlePiece> mChangedPhotos = new ArrayList<>();

    private boolean mNeedDrawBorder = false;
    private boolean mMoveLineEnable = true;
    private boolean mNeedDrawOuterBorder = false;

    public PuzzleView(Context context) {
        this(context, null, 0);
    }

    public PuzzleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PuzzleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mBorderRect = new RectF();

        mBitmapPaint = new Paint();
        mBitmapPaint.setAntiAlias(true);
        mBitmapPaint.setFilterBitmap(true);

        mBorderPaint = new Paint();

        mBorderPaint.setAntiAlias(true);
        mBorderPaint.setColor(Color.WHITE);
        mBorderPaint.setStrokeWidth(mBorderWidth);

    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mPuzzleLayout == null || mPuzzleLayout.getBorderSize() == 0) {
            Log.e(TAG, "the puzzle layout or its border can not be null");
            return;
        }

        for (int i = 0; i < mPuzzleLayout.getBorderSize(); i++) {
            Border border = mPuzzleLayout.getBorder(i);
            canvas.save();
            canvas.clipRect(border.getRect());
            if (mPuzzlePieces.size() > i)
                mPuzzlePieces.get(i).draw(canvas, mBitmapPaint);
            canvas.restore();
        }

        if (mNeedDrawBorder) {
            for (Line line : mPuzzleLayout.getLines()) {
                drawLine(canvas, line);
            }
        }

        //draw outer line
        if (mNeedDrawOuterBorder) {
            for (Line line : mPuzzleLayout.getOuterLines()) {
                drawLine(canvas, line);
            }
        }

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mMoveLineEnable) {
            return super.onTouchEvent(event);
        }

        switch (MotionEventCompat.getActionMasked(event)) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();

                mHandlingLine = findHandlingLine();

                if (mHandlingLine != null) {
                    mCurrentMode = Mode.MOVE;
                    mChangedPhotos.clear();
                    mChangedPhotos.addAll(findChangedPhoto());

                    for (int i = 0; i < mChangedPhotos.size(); i++) {
                        mChangedPhotos.get(i).getDownMatrix().set(mChangedPhotos.get(i).getMatrix());
                    }

                } else {
                    mHandlingPiece = findHandlingPhoto();
                    if (mHandlingPiece != null) {
                        mCurrentMode = Mode.DRAG;
                        mHandlingPiece.getDownMatrix().set(mHandlingPiece.getMatrix());
                    }
                }

                break;

            case MotionEvent.ACTION_POINTER_DOWN:

                mOldDistance = calculateDistance(event);
                mMidPoint = calculateMidPoint(event);

                if (mHandlingPiece != null
                        && isInPhotoArea(mHandlingPiece, event.getX(1), event.getY(1))
                        && mCurrentMode != Mode.MOVE) {
                    mCurrentMode = Mode.ZOOM;
                }
                break;


            case MotionEvent.ACTION_MOVE:
                switch (mCurrentMode) {
                    case NONE:
                        break;
                    case DRAG:
                        if (mHandlingPiece != null) {
                            mHandlingPiece.getMatrix().set(mHandlingPiece.getDownMatrix());
                            mHandlingPiece.getMatrix().postTranslate(event.getX() - mDownX, event.getY() - mDownY);

                            mHandlingPiece.setTranslateX(mHandlingPiece.getMappedCenterPoint().x
                                    - mHandlingPiece.getBorder().centerX());

                            mHandlingPiece.setTranslateY(mHandlingPiece.getMappedCenterPoint().y
                                    - mHandlingPiece.getBorder().centerY());
                        }
                        break;
                    case ZOOM:

                        if (mHandlingPiece != null && event.getPointerCount() >= 2) {
                            float newDistance = calculateDistance(event);

                            mHandlingPiece.getMatrix().set(mHandlingPiece.getDownMatrix());
                            mHandlingPiece.getMatrix().postScale(
                                    newDistance / mOldDistance, newDistance / mOldDistance, mMidPoint.x, mMidPoint.y);

                            mHandlingPiece.setScaleFactor(mHandlingPiece.getMappedWidth() / mHandlingPiece.getWidth());
                        }

                        break;
                    case MOVE:
                        moveLine(event);
                        mPuzzleLayout.update();
                        updatePhotoInBorder(event);
                        break;
                }

                invalidate();
                break;

            case MotionEvent.ACTION_UP:
                mHandlingLine = null;

                if (mCurrentMode == Mode.DRAG || mCurrentMode == Mode.ZOOM) {
                    if (!mHandlingPiece.isFilledBorder()) {
                        fillBorder(mHandlingPiece);
                        mHandlingPiece.setScaleFactor(0f);
                    }
                }

                mCurrentMode = Mode.NONE;
                invalidate();
                break;

            case MotionEvent.ACTION_POINTER_UP:

                break;
        }
        return true;

    }


    /**
     * let piece fill with its border
     *
     * @param piece puzzle piece which can not be null
     * @return the scale factor to fill with border
     */
    private void fillBorder(PuzzlePiece piece) {
        piece.getMatrix().reset();

        final RectF rectF = piece.getBorder().getRect();

        float offsetX = rectF.centerX() - piece.getWidth() / 2;
        float offsetY = rectF.centerY() - piece.getHeight() / 2;

        piece.getMatrix().postTranslate(offsetX, offsetY);
        float scale = calculateFillScaleFactor(piece);

        piece.getMatrix().postScale(scale, scale, rectF.centerX(), rectF.centerY());

        piece.setTranslateX(0f);
        piece.setTranslateY(0f);
        piece.setScaleFactor(0f);
    }

    private float calculateFillScaleFactor(PuzzlePiece piece) {
        final RectF rectF = piece.getBorder().getRect();
        float scale;
        if (piece.getWidth() * rectF.height() > rectF.width() * piece.getHeight()) {
            scale = (rectF.height() + mExtraSize) / piece.getHeight();
        } else {
            scale = (rectF.width() + mExtraSize) / piece.getWidth();
        }
        return scale;
    }

    private float calculateFillScaleFactor(PuzzlePiece piece, Border border) {
        final RectF rectF = border.getRect();
        float scale;
        if (piece.getWidth() * rectF.height() > rectF.width() * piece.getHeight()) {
            scale = rectF.height() / piece.getHeight();
        } else {
            scale = rectF.width() / piece.getWidth();
        }
        return scale;
    }


    //TODO
    private void updatePhotoInBorder(MotionEvent event) {
        for (PuzzlePiece piece : mChangedPhotos) {
            float scale = calculateFillScaleFactor(piece, mPuzzleLayout.getOuterBorder());

            if (piece.getScaleFactor() > scale && piece.isFilledBorder()) {
                piece.getMatrix().set(piece.getDownMatrix());

                if (mHandlingLine.getDirection() == Line.Direction.HORIZONTAL) {
                    piece.getMatrix().postTranslate(0, (event.getY() - mDownY) / 2);
                } else if (mHandlingLine.getDirection() == Line.Direction.VERTICAL) {
                    piece.getMatrix().postTranslate((event.getX() - mDownX) / 2, 0);
                }

            } else if (piece.isFilledBorder() && (piece.getTranslateX() != 0f || piece.getTranslateY() != 0f)) {
                piece.getMatrix().set(piece.getDownMatrix());

                if (mHandlingLine.getDirection() == Line.Direction.HORIZONTAL) {
                    piece.getMatrix().postTranslate(0, (event.getY() - mDownY) / 2);
                } else if (mHandlingLine.getDirection() == Line.Direction.VERTICAL) {
                    piece.getMatrix().postTranslate((event.getX() - mDownX) / 2, 0);
                }

            } else {
                fillBorder(piece);
            }
        }

    }

    private List<PuzzlePiece> findChangedPhoto() {
        if (mHandlingLine == null) return new ArrayList<>();

        List<PuzzlePiece> puzzlePieces = new ArrayList<>();

        for (PuzzlePiece piece : mPuzzlePieces) {
            if (piece.getBorder().contains(mHandlingLine)) {
                puzzlePieces.add(piece);
            }
        }

        return puzzlePieces;
    }


    private void moveLine(MotionEvent event) {
        if (mHandlingLine == null) {
            return;
        }

        if (mHandlingLine.getDirection() == Line.Direction.HORIZONTAL) {
            mHandlingLine.moveTo(event.getY(), 20);
        } else if (mHandlingLine.getDirection() == Line.Direction.VERTICAL) {
            mHandlingLine.moveTo(event.getX(), 20);
        }


    }

    private Line findHandlingLine() {
        for (Line line : mPuzzleLayout.getLines()) {
            if (line.contains(mDownX, mDownY, 20)) {
                return line;
            }
        }
        return null;
    }

    private PuzzlePiece findHandlingPhoto() {
        for (PuzzlePiece photo : mPuzzlePieces) {
            if (photo.contains(mDownX, mDownY)) {
                return photo;
            }
        }
        return null;
    }

    private boolean isInPhotoArea(PuzzlePiece handlingPhoto, float x, float y) {
        return handlingPhoto.contains(x, y);
    }

    private float calculateDistance(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);

        return (float) Math.sqrt(x * x + y * y);
    }

    private PointF calculateMidPoint(MotionEvent event) {
        float x = (event.getX(0) + event.getX(1)) / 2;
        float y = (event.getY(0) + event.getY(1)) / 2;
        return new PointF(x, y);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBorderRect.left = getPaddingLeft();
        mBorderRect.top = getPaddingTop();
        mBorderRect.right = w - getPaddingRight();
        mBorderRect.bottom = h - getPaddingBottom();

        if (mPuzzleLayout != null) {
            mPuzzleLayout.setOuterBorder(mBorderRect);
            mPuzzleLayout.layout();
        }

        if (mPuzzlePieces.size() != 0) {
            for (int i = 0; i < mPuzzlePieces.size(); i++) {
                PuzzlePiece piece = mPuzzlePieces.get(i);
                piece.setBorder(mPuzzleLayout.getBorder(i));
                piece.getMatrix().set(
                        BorderUtil.createMatrix(mPuzzleLayout.getBorder(i), piece.getWidth(), piece.getHeight(), mExtraSize));
            }
        }

        invalidate();

    }


    public void addPiece(final Bitmap bitmap) {
        int index = mPuzzlePieces.size();

        if (index >= mPuzzleLayout.getBorderSize()) {
            Log.e(TAG, "addPiece: can not add more. the current puzzle layout can contains "
                    + mPuzzleLayout.getBorderSize() + " puzzle piece.");
            return;
        }

        Matrix matrix = BorderUtil.createMatrix(mPuzzleLayout.getBorder(index), bitmap, mExtraSize);

        BitmapPiece layoutPhoto = new BitmapPiece(bitmap, mPuzzleLayout.getBorder(index), matrix);

        mPuzzlePieces.add(layoutPhoto);

        invalidate();
    }

    public void addPieces(final List<Bitmap> bitmaps) {
        for (Bitmap bitmap : bitmaps) {
            int index = mPuzzlePieces.size();

            if (index >= mPuzzleLayout.getBorderSize()) {
                Log.e(TAG, "addPiece: can not add more. the current puzzle layout can contains "
                        + mPuzzleLayout.getBorderSize() + " puzzle piece.");
                return;
            }

            Matrix matrix = BorderUtil.createMatrix(mPuzzleLayout.getBorder(index), bitmap, mExtraSize);

            BitmapPiece layoutPhoto = new BitmapPiece(bitmap, mPuzzleLayout.getBorder(index), matrix);

            mPuzzlePieces.add(layoutPhoto);
        }

        invalidate();
    }

    private void drawLine(Canvas canvas, Line line) {
        canvas.drawLine(line.start.x, line.start.y, line.end.x, line.end.y, mBorderPaint);
    }

    public void reset() {
        mHandlingLine = null;
        mHandlingPiece = null;

        if (mPuzzleLayout != null) {
            mPuzzleLayout.reset();
        }
        mPuzzlePieces.clear();
        mChangedPhotos.clear();

        invalidate();
    }

    public PuzzleLayout getPuzzleLayout() {
        return mPuzzleLayout;
    }

    public void setPuzzleLayout(PuzzleLayout puzzleLayout) {
        reset();

        mPuzzleLayout = puzzleLayout;
        mPuzzleLayout.setOuterBorder(mBorderRect);
        mPuzzleLayout.layout();

        invalidate();
    }

    public float getBorderWidth() {
        return mBorderWidth;
    }

    public void setBorderWidth(float borderWidth) {
        mBorderWidth = borderWidth;
    }

    public boolean isNeedDrawBorder() {
        return mNeedDrawBorder;
    }

    public void setNeedDrawBorder(boolean needDrawBorder) {
        mNeedDrawBorder = needDrawBorder;
        invalidate();
    }

    public boolean isMoveLineEnable() {
        return mMoveLineEnable;
    }

    public void setMoveLineEnable(boolean moveLineEnable) {
        mMoveLineEnable = moveLineEnable;
    }

    public float getExtraSize() {
        return mExtraSize;
    }

    public void setExtraSize(float extraSize) {
        if (extraSize < 0) {
            Log.e(TAG, "setExtraSize: the extra size must be greater than 0");
            mExtraSize = 0;
        } else {
            mExtraSize = extraSize;
        }
    }

    public boolean isNeedDrawOuterBorder() {
        return mNeedDrawOuterBorder;
    }

    public void setNeedDrawOuterBorder(boolean needDrawOuterBorder) {
        mNeedDrawOuterBorder = needDrawOuterBorder;
    }

}
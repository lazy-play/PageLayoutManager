package com.pudding.pagelayoutmanager;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;


/**
 * Created by Error on 2017/9/26 0026.
 */

public class PageLayoutManager extends RecyclerView.LayoutManager {
    private RecyclerView mRecyclerView = null;
    private int totalWidth;
    private int rows = 0;
    private int columns = 0;
    private int onePageSize = 0;

    private int pageSize = 0;

    private int itemWidth = 0;
    private int itemHeight = 0;

    private int itemWidthUsed;
    private int itemHeightUsed;

    private int marginsWidth = 0;
    private int marginsHeight = 0;

    private int offsetX = 0;
    private int startX = 0;
    private int mState = 0;

    private boolean atEdge = false;
    private boolean startedAnimation = true;
    private int edgeDir = 0;
    private int dir = 0;
    private Rect marginRect;

    /**
     * 用于保存item的位置信息
     */
    private SparseArray<Rect> allItemFrames = new SparseArray<>();
    /**
     * 用于保存item是否处于可见状态的信息
     */
    private SparseBooleanArray itemStates = new SparseBooleanArray();

    private RecyclerView.OnScrollListener mOnScrollListener = new PageOnScrollListener();
    private RecyclerView.OnFlingListener mOnFlingListener = new PageOnFlingListener();
    private Animator.AnimatorListener animatorListener = new PageAnimatorListener();
    private OnCallBackNeedDate myDate;
    private ItemTouchHelper itemTouchHelper;
    private ValueAnimator.AnimatorUpdateListener updateListener;
    private int lastNum;
    private ValueAnimator animator;

    public PageLayoutManager(int rows, int columns) {
        if (rows < 1) {
            throw new IllegalArgumentException("row count should be at least 1. Provided "
                    + rows);
        }
        if (columns < 1) {
            throw new IllegalArgumentException("column count should be at least 1. Provided "
                    + columns);
        }
        this.rows = rows;
        this.columns = columns;
        onePageSize = rows * columns;
    }

    public void bindRecyclerView(RecyclerView recycleView) {
        if (recycleView == null) {
            throw new IllegalArgumentException("recycleView must be not null");
        }
        mRecyclerView = recycleView;
        //处理滑动
        recycleView.setOnFlingListener(mOnFlingListener);
        //设置滚动监听，记录滚动的状态，和总的偏移量
        recycleView.addOnScrollListener(mOnScrollListener);
        //记录滚动开始的位置
        //获取滚动的方向
        updateLayoutManger();
    }
    public void setOnCallBackNeedDate(OnCallBackNeedDate needDate){
        myDate=needDate;
    }
public void enableDragItem(boolean enable){
    if(mRecyclerView==null){
        throw new RuntimeException("you should call setUpRecycleView(RecyclerView) first");
    }
    if (myDate==null){
        throw new RuntimeException("you should call setOnCallBackNeedDate(OnCallBackNeedDate) first");
    }
    if(enable){
        if(itemTouchHelper==null){
            PageDragCallBack callBack=new PageDragCallBack(myDate);
            itemTouchHelper = new ItemTouchHelper(callBack);
        }
        itemTouchHelper.attachToRecyclerView(mRecyclerView);
    }else{
        itemTouchHelper.attachToRecyclerView(null);
    }
}
    public void setMarginHorizontal(int marginHorizontal) {
        marginsWidth = marginHorizontal;
    }

    public void setMarginVertical(int marginVertical) {
        marginsHeight = marginVertical;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getItemCount() <= 0 || state.isPreLayout()) {
            return;
        }
         /* 这个方法主要用于计算并保存每个ItemView的位置 */
        calculateChildrenSite(recycler);
        recycleAndFillItems(recycler);
    }

    private void recycleAndFillItems(RecyclerView.Recycler recycler) {
//        long time=System.currentTimeMillis();
        Rect displayRect = new Rect(getPaddingLeft() - getWidth() / 2 + offsetX, getPaddingTop(), getWidth() * 3 / 2 + offsetX - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom());
        if (getChildCount() == 0) {
            //重新显示需要出现在屏幕的子View
            int start = ((lastNum - 2 * onePageSize) > 0 ? (lastNum - 2 * onePageSize) : 0);
            int end = (2 * onePageSize + lastNum) > getItemCount() ? getItemCount() : (2 * onePageSize + lastNum);
            for (int i = start; i < end; i++) {
                //判断ItemView的位置和当前显示区域是否重合
                buildRectByPosition(i);
                if (Rect.intersects(displayRect, allItemFrames.get(i))) {
                    //获得Recycler中缓存的View
                    View itemView = recycler.getViewForPosition(i);
                    addView(itemView);
                    measureChildWithMargins(itemView, itemWidthUsed, itemHeightUsed);
                    //添加View到RecyclerView上
                    //取出先前存好的ItemView的位置矩形
                    Rect rect = allItemFrames.get(i);
                    //将这个item布局出来
                    layoutDecorated(itemView, rect.left - offsetX, rect.top, rect.right - offsetX, rect.bottom);
                    itemStates.put(i, true); //更新该View的状态为依附
                }
            }
        } else {
            Rect childRect = new Rect();
            int firstPosition = getPosition(getChildAt(0));
            int lastPosition = getPosition(getChildAt(getChildCount() - 1));
            for (int i = 0; i < getChildCount(); i++) {
                //这个方法获取的是RecyclerView中的View，注意区别Recycler中的View
                //这获取的是实际的View
                View child = getChildAt(i);

                //下面几个方法能够获取每个View占用的空间的位置信息，包括ItemDecorator
                childRect.left = getDecoratedLeft(child) + offsetX;
                childRect.top = getDecoratedTop(child);
                childRect.right = getDecoratedRight(child) + offsetX;
                childRect.bottom = getDecoratedBottom(child);
                //如果Item没有在显示区域，就说明需要回收
                if (!Rect.intersects(displayRect, childRect)) {
                    //移除并回收掉滑出屏幕的View
                    removeAndRecycleView(child, recycler);
                    itemStates.put(getPosition(child), false); //更新该View的状态为未依附
                }
            }
            if (dir > 0) {
                int end = (onePageSize + lastPosition) > getItemCount() ? getItemCount() : (onePageSize + lastPosition);
                for (int i = firstPosition; i < end; i++) {
                    buildRectByPosition(i);
                    if (Rect.intersects(displayRect, allItemFrames.get(i))) {
                        if (itemStates.get(i)) {
                            continue;
                        }
                        addItemView(recycler, i);
                    }
                }
            } else {
                int start = ((firstPosition - onePageSize) > 0 ? (firstPosition - onePageSize) : 0);
                for (int i = lastPosition; i >= start; i--) {
                    buildRectByPosition(i);
                    if (Rect.intersects(displayRect, allItemFrames.get(i))) {
                        if (itemStates.get(i)) {
                            continue;
                        }
                        addItemView(recycler, i, 0);
                    }
                }
            }
        }
//        System.out.println("recycleAndFillItems:"+(System.currentTimeMillis()-time));
    }

    private void addItemView(RecyclerView.Recycler recycler, int viewPosition) {
        addItemView(recycler, viewPosition, -1);
    }

    private void addItemView(RecyclerView.Recycler recycler, int viewPosition, int toIndex) {
//获得Recycler中缓存的View
        View itemView = recycler.getViewForPosition(viewPosition);
        measureChildWithMargins(itemView, itemWidthUsed, itemHeightUsed);
        //添加View到RecyclerView上
        addView(itemView, toIndex);
        //取出先前存好的ItemView的位置矩形
        Rect rect = allItemFrames.get(viewPosition);
        //将这个item布局出来
        layoutDecorated(itemView, rect.left - offsetX, rect.top, rect.right - offsetX, rect.bottom);
        itemStates.put(viewPosition, true); //更新该View的状态为依附
    }

    private void calculateChildrenSite(RecyclerView.Recycler recycler) {
//        long time=System.currentTimeMillis();
        View v0 = recycler.getViewForPosition(0);
        ViewGroup.MarginLayoutParams layoutParams = ((ViewGroup.MarginLayoutParams) v0.getLayoutParams());
        marginRect = new Rect();
        marginRect.left = layoutParams.leftMargin;
        marginRect.top = layoutParams.topMargin;
        marginRect.right = layoutParams.rightMargin;
        marginRect.bottom = layoutParams.bottomMargin;
        //获取每个Item的平均宽高
        itemWidth = (getUsableWidth() - marginsWidth * 2) / columns - (marginRect.left + marginRect.right);
        itemHeight = (getUsableHeight() - marginsHeight * 2) / rows - (marginRect.top + marginRect.bottom);

        //计算宽高已经使用的量，主要用于后期测量
        itemWidthUsed = (columns - 1) * (itemWidth + (marginRect.left + marginRect.right)) + marginsWidth * 2;
        itemHeightUsed = (rows - 1) * (itemHeight + (marginRect.top + marginRect.bottom)) + marginsHeight * 2;

        //计算总的页数
        pageSize = getItemCount() / onePageSize + (getItemCount() % onePageSize == 0 ? 0 : 1);

        //计算可以横向滚动的最大值
        totalWidth = (pageSize) * getWidth();
        if (getChildAt(0) != null)
            lastNum = getPosition(getChildAt(0));
        detachAndScrapAttachedViews(recycler);
        itemStates.clear();
        allItemFrames.clear();
//        System.out.println("calculateChildrenSite:"+(System.currentTimeMillis()-time));
    }

    private void buildRectByPosition(int position) {
        int p = position / onePageSize;
        int remainder = position % onePageSize;
        int r = remainder / columns;
        int c = remainder % columns;

        Rect rect = allItemFrames.get(position);
        if (rect == null) {
            rect = new Rect();
        }
        int x = p * getUsableWidth() + marginsWidth + c * (itemWidth + marginRect.left + marginRect.right);
        int y = marginsHeight + r * (itemHeight + marginRect.top + marginRect.bottom);
        rect.set(x + marginRect.left, y + marginRect.top, x + itemWidth + marginRect.left, y + itemHeight + marginRect.top);
        allItemFrames.put(position, rect);
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int travel = 0;
        dir = (int) Math.signum(dx);
        if (mRecyclerView != null)
            if (mState == 0) {
                if (!atEdge) {
                    atEdge = true;
                    edgeDir = dir;
                    mRecyclerView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startedAnimation = false;
                        }
                    }, 500);
                    mRecyclerView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!startedAnimation) {
                                startedAnimation = true;
                                atEdge = false;
                            }
                        }
                    }, 700);
                }

                if (!startedAnimation) {
                    startedAnimation = true;
                    if (edgeDir == dir) {
                        travel = edgeDir * mRecyclerView.getWidth();
                        if (offsetX + edgeDir * mRecyclerView.getWidth() < 0) {
                            travel = -offsetX;
                        } else if (offsetX + edgeDir * mRecyclerView.getWidth() > totalWidth - getHorizontalSpace()) {//如果滑动到最底部
                            travel = totalWidth - getHorizontalSpace() - offsetX;
                        }
                        if (animator == null) {
                            animator = ValueAnimator.ofInt(0, travel);
                            animator.setDuration(500);
                            animator.addListener(animatorListener);
                        } else {
                            animator.setIntValues(0, travel);
                        }
                        animator.removeUpdateListener(updateListener);
                        updateListener = new PageUpdateListener(recycler);
                        animator.addUpdateListener(updateListener);
                        animator.start();
                    } else {
                        atEdge = false;
                    }
                }
                return 0;
            }

        //列表向下滚动dy为正，列表向上滚动dy为负，这点与Android坐标系保持一致。
        //实际要滑动的距离
        travel = dx;
        //如果滑动到最顶部
        if (offsetX + dx < 0) {
            travel = -offsetX;
        } else if (offsetX + dx > totalWidth - getHorizontalSpace()) {//如果滑动到最底部
            travel = totalWidth - getHorizontalSpace() - offsetX;
        }
        //将竖直方向的偏移量+travel
        offsetX += travel;
        // 调用该方法通知view在y方向上移动指定距离
        offsetChildrenHorizontal(-travel);
        recycleAndFillItems(recycler);
        return travel;
    }

    private int getHorizontalSpace() {
        //计算RecyclerView的可用高度，除去上下Padding值
        return getWidth() - getPaddingRight() - getPaddingLeft();
    }

    private int getUsableWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    private int getUsableHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    @Override
    public boolean canScrollHorizontally() {
        return true;
    }

    @Override
    public boolean canScrollVertically() {
        return false;
    }

    public void updateLayoutManger() {
        RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        if (layoutManager != null) {
            startX = 0;
            offsetX = 0;
        }
    }

    private int getStartPageIndex() {
        int p = 0;
        p = startX / mRecyclerView.getWidth();
        return p;
    }

    public class PageAnimatorListener extends AnimatorListenerAdapter {
        @Override
        public void onAnimationEnd(Animator animation) {
            atEdge = false;
        }
    }

    public class PageUpdateListener implements ValueAnimator.AnimatorUpdateListener {
        int lastValue;
        RecyclerView.Recycler recycler;

        public PageUpdateListener(RecyclerView.Recycler recycler) {
            this.recycler = recycler;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            int value = (int) animation.getAnimatedValue() - lastValue;
            lastValue = (int) animation.getAnimatedValue();
            //将竖直方向的偏移量+travel
            offsetX += value;
            // 调用该方法通知view在x方向上移动指定距离
            offsetChildrenHorizontal(-value);
            recycleAndFillItems(recycler);
        }
    }

    public class PageOnScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            mState = newState;
            startX = offsetX;
            if (newState == 0) {
                int dx = startX % mRecyclerView.getWidth();
                if (dx != 0) {
                    int p = getStartPageIndex() + ((dx > mRecyclerView.getWidth() / 2) ? 1 : 0);
                    int endPoint = 0;
                    endPoint = p * mRecyclerView.getWidth();
                    if (endPoint < 0) {
                        endPoint = 0;
                    }
                    mRecyclerView.smoothScrollBy(endPoint - offsetX, 0);
                }
            }
        }
    }

    public class PageOnFlingListener extends RecyclerView.OnFlingListener {
        @Override
        public boolean onFling(int velocityX, int velocityY) {
            int dx = startX % mRecyclerView.getWidth();
            //获取开始滚动时所在页面的index
            int p = getStartPageIndex() + ((dx > mRecyclerView.getWidth() / 2) ? 1 : 0);

            //记录滚动开始和结束的位置
            int endPoint = 0;
            if (velocityX < 0) {
                p--;
            } else if (velocityX > 0) {
                p++;
            }
            endPoint = p * mRecyclerView.getWidth();
            if (endPoint < 0) {
                endPoint = 0;
            }
            //使用动画处理滚动
            mRecyclerView.smoothScrollBy(endPoint - offsetX, 0);
            return true;
        }
    }
}

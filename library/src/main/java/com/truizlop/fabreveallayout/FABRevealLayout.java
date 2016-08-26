/*
 * Copyright (C) 2015 Tomás Ruiz-López.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.truizlop.fabreveallayout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.List;

public class FABRevealLayout extends RelativeLayout {

    private int fabSizePx;
    private float fabMaxSize = 48;
    private int fabOrientation = 0; /* 0 = vertical, 1 = horizontal */
    private static final int ANIMATION_DURATION = 500;
    private final Interpolator INTERPOLATOR = new FastOutSlowInInterpolator();

    private List<View> childViews = null;
    private List<FloatingActionButton> fabs = new ArrayList<>();

    private int fabIndex = 0, indexAnimated = -1;
    private CircularExpandingView circularExpandingView = null;
    private OnRevealChangeListener onRevealChangeListener = null;

    public FABRevealLayout(Context context) {
        this(context, null);
        childViews = new ArrayList<>();
        fabIndex = 0;
        fabs = new ArrayList<>();
        setGlobalListener();
    }

    public FABRevealLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.FABRevealLayout,
                0, 0);

        try {
            fabMaxSize = a.getDimension(R.styleable.FABRevealLayout_fabMaxSize, 24);
            fabOrientation = a.getInt(R.styleable.FABRevealLayout_faborientation, 0);
        } finally {
            a.recycle();
        }
        setGlobalListener();
        childViews = new ArrayList<>();
        fabIndex = 0;
        fabs = new ArrayList<>();
    }

    public FABRevealLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.FABRevealLayout,
                0, 0);

        try {
            fabMaxSize = a.getDimension(R.styleable.FABRevealLayout_fabMaxSize, 24);
            fabOrientation = a.getInteger(R.styleable.FABRevealLayout_faborientation, 0);
        } finally {
            a.recycle();
        }
        setGlobalListener();
        childViews = new ArrayList<>();
        fabIndex = 0;
        fabs = new ArrayList<>();
    }

    private void setGlobalListener() {
        final RelativeLayout me = this;
        final ViewTreeObserver vto = this.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    me.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                setupInitialState();
            }
        });
    }

    private void calculateFabOptimalSize() {
        if (fabOrientation == 0) {
            int heightPx = getMeasuredHeight();
            int padding = dipsToPixels(16f);
            fabSizePx = Math.min(dipsToPixels(fabMaxSize), (heightPx - fabs.size() * padding) / fabs.size());
            Log.e("BEST", "Total height: " + heightPx +
                    ", max height: " + dipsToPixels(fabMaxSize) +
                    ", optimal : " + (heightPx - fabs.size() * padding) / fabs.size() +
                    ", chosen : " + fabSizePx + " px");
        } else {
            int widthPx = getMeasuredWidth();
            int padding = dipsToPixels(16f);
            fabSizePx = Math.min(dipsToPixels(fabMaxSize), (widthPx - fabs.size() * padding) / fabs.size());
            Log.e("BEST", "Total height: " + widthPx +
                    ", max height: " + dipsToPixels(fabMaxSize) +
                    ", optimal : " + (widthPx - fabs.size() * padding) / fabs.size() +
                    ", chosen : " + fabSizePx + " px");
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        setupView(child);
        super.addView(child, index, params);
    }

    private void setupView(View child) {
        if (child instanceof FloatingActionButton) {
            setupFAB(child);
        } else if (!(child instanceof CircularExpandingView)) {
            setupChildView(child);
        }
    }

    private void setupFAB(View view) {
        view.setTag(fabIndex);
        fabIndex++;
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (indexAnimated == -1) {
                    indexAnimated = (int) v.getTag();
                    revealSecondaryView((int) v.getTag());
                    startHideAnimationUnusedFAB(indexAnimated);
                }
            }
        });
        fabs.add((FloatingActionButton) view);
    }

    private void setupChildView(View view) {
        childViews.add(view);
    }

    private void addCircularRevealView() {
        circularExpandingView = new CircularExpandingView(getContext());
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params.topMargin = fabSizePx;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            circularExpandingView.setZ(-1);
        }
        circularExpandingView.setVisibility(View.GONE);
        addView(circularExpandingView, params);
    }

    private void setupInitialState() {
        calculateFabOptimalSize();
        setupFABPosition();
        setupChildViewsPosition();
        addCircularRevealView();
    }

    private void setupFABPosition() {
        for (int i = 0; i < fabs.size(); i++) {
            FloatingActionButton fab = fabs.get(i);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) fab.getLayoutParams();
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
            params.height = fabSizePx;
            params.width = fabSizePx;

            if (fabOrientation == 0) {
                    /* Vertical */
                params.rightMargin = dipsToPixels(16);
                params.topMargin = dipsToPixels(20) + fabSizePx*i + dipsToPixels(8) * i;
            } else {
                    /* Horizontal */
                params.rightMargin = dipsToPixels(20) + fabSizePx*i + dipsToPixels(8) * i;
//                params.topMargin = fabSizePx / 2;
            }

            fab.bringToFront();
        }

    }

    private void setupChildViewsPosition() {
        for (int i = 0; i < childViews.size(); i++) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) childViews.get(i).getLayoutParams();
            params.topMargin = fabSizePx;
        }
        for (int i = 0; i < fabs.size(); i++) {
            getSecondaryView(i).setVisibility(GONE);
        }
    }

    private boolean isShowingMainView(int index) {
        return getMainView(index).getVisibility() == VISIBLE;
    }

    public void revealMainView(int index) {
        if (!isShowingMainView(index)) {
            startHideAnimation(index);
        }
    }

    public void revealSecondaryView(int index) {
        if (isShowingMainView(index)) {
            startRevealAnimation(index);
        }
    }

    public void setOnRevealChangeListener(OnRevealChangeListener onRevealChangeListener) {
        this.onRevealChangeListener = onRevealChangeListener;
    }

    private void startRevealAnimation(final int index) {
        View disappearingView = getMainView(index);

        ObjectAnimator fabAnimator = getFABAnimator(index);
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(disappearingView, "alpha", 1, 0);

        AnimatorSet set = new AnimatorSet();
        set.play(fabAnimator).with(alphaAnimator);
        setupAnimationParams(set);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                FloatingActionButton fab = fabs.get(index);
                fab.setVisibility(GONE);
                prepareForReveal(index);
                expandCircle(index);
            }
        });

        set.start();
    }

    private void prepareForReveal(int index) {
        FloatingActionButton fab = fabs.get(index);
        circularExpandingView.getLayoutParams().height = getMainView(index).getHeight();
        circularExpandingView.setColor(fab.getBackgroundTintList() != null ?
                fab.getBackgroundTintList().getDefaultColor() - (0x80 << 24) :
                0x80000000
        );
        circularExpandingView.setVisibility(VISIBLE);
    }

    private void setupAnimationParams(Animator animator) {
        animator.setInterpolator(INTERPOLATOR);
        animator.setDuration(ANIMATION_DURATION);
    }

    private CurvedAnimator getCurvedAnimator(int index) {
        View view = getMainView(index);
        FloatingActionButton fab = fabs.get(index);
        float fromX = fab.getLeft();
        float fromY = fab.getTop();
        float toX = view.getWidth() / 2 - fab.getWidth() / 2 + view.getLeft();
        float toY = view.getHeight() / 2 - fab.getHeight() / 2 + view.getTop();

        if (isShowingMainView(index)) {
            return new CurvedAnimator(fromX, fromY, toX, toY);
        } else {
            return new CurvedAnimator(toX, toY, fromX, fromY);
        }
    }

    private ObjectAnimator getFABAnimator(int index) {
        CurvedAnimator curvedAnimator = getCurvedAnimator(index);
        return ObjectAnimator.ofObject(this, "fabPosition", new CurvedPathEvaluator(), curvedAnimator.getPoints());
    }

    private void expandCircle(final int index) {
        Animator expandAnimator = circularExpandingView.expand();
        ;
        expandAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                swapViews(index);
            }
        });
        expandAnimator.start();
    }

    private void startHideAnimationUnusedFAB(final int index) {
        List<AnimatorSet> sets = new ArrayList<>(fabs.size() - 1);
        for (int i = 0; i < fabs.size(); i++) {
            if (i != index) {
                Animator contractAnimator = circularExpandingView.contract();
                View disappearingView = fabs.get(i);
                ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(disappearingView, "alpha", 1, 0);
                AnimatorSet set = new AnimatorSet();
                set.play(contractAnimator).with(alphaAnimator);
                setupAnimationParams(set);
                sets.add(set);
            }
        }
        for (AnimatorSet set : sets) {
            set.start();
        }
    }

    private void startShowAnimationUnusedFAB(final int index) {
        List<AnimatorSet> sets = new ArrayList<>(fabs.size() - 1);
        for (int i = 0; i < fabs.size(); i++) {
            if (i != index) {
                Animator contractAnimator = circularExpandingView.contract();
                View disappearingView = fabs.get(i);
                ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(disappearingView, "alpha", 0, 1);
                AnimatorSet set = new AnimatorSet();
                set.play(contractAnimator).with(alphaAnimator);
                setupAnimationParams(set);
                sets.add(set);
            }
        }
        for (AnimatorSet set : sets) {
            set.start();
        }
    }


    private void startHideAnimation(final int index) {
        Animator contractAnimator = circularExpandingView.contract();
        View disappearingView = getSecondaryView(index);
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(disappearingView, "alpha", 1, 0);

        AnimatorSet set = new AnimatorSet();
        set.play(contractAnimator).with(alphaAnimator);
        setupAnimationParams(set);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                FloatingActionButton fab = fabs.get(index);
                fab.setVisibility(VISIBLE);
                circularExpandingView.setVisibility(GONE);
                moveFABToOriginalLocation(index);
                startShowAnimationUnusedFAB(index);
            }
        });
        set.start();
    }

    public void setFabPosition(Point point) {
        FloatingActionButton fab = fabs.get(indexAnimated);
        fab.setX(point.x);
        fab.setY(point.y);
    }

    private void moveFABToOriginalLocation(final int index) {
        ObjectAnimator fabAnimator = getFABAnimator(index);

        setupAnimationParams(fabAnimator);
        fabAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                swapViews(index);
                indexAnimated = -1;
            }
        });

        fabAnimator.start();
    }

    private void swapViews(int index) {
        if (isShowingMainView(index)) {
            getMainView(index).setVisibility(GONE);
            getMainView(index).setAlpha(1);
            getSecondaryView(index).setVisibility(VISIBLE);
            circularExpandingView.setVisibility(VISIBLE);
        } else {
            getMainView(index).setVisibility(VISIBLE);
            getSecondaryView(index).setVisibility(GONE);
            getSecondaryView(index).setAlpha(1);
            circularExpandingView.setVisibility(View.GONE);
        }
        notifyListener(index);
    }

    private void notifyListener(int index) {
        if (onRevealChangeListener != null) {
            if (isShowingMainView(index)) {
                onRevealChangeListener.onMainViewAppeared(this, getMainView(index));
            } else {
                onRevealChangeListener.onSecondaryViewAppeared(this, getSecondaryView(index));
            }
        }
    }

    private View getSecondaryView(int index) {
        return childViews.get(2 * index + 1);
    }

    private View getMainView(int index) {
        return childViews.get(2 * index);
    }

    private int dipsToPixels(float dips) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dips, getResources().getDisplayMetrics());
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        ((MarginLayoutParams) params).topMargin -= dipsToPixels(fabMaxSize);
        super.setLayoutParams(params);
    }

}

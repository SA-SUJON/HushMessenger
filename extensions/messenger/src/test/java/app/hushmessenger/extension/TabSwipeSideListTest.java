package app.hushmessenger.extension;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Swipe between tabs next to a row that scrolls sideways (#258). People's story cards on 588 are a horizontal
 * RecyclerView Litho mounts inside the tab's page. A RecyclerView asks to keep a drag only once a step of it actually
 * scrolled, so at the start of the cards, or on a quick first move, the frame took the swipe and changed tab. A touch
 * that goes down on anything that scrolls sideways now belongs to it for the whole gesture.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class TabSwipeSideListTest {
    private static final String HOME = "Lapp/hushmessenger/extension/TabSwipeTest$Home;";
    private static final String BAR = "Lapp/hushmessenger/extension/TabSwipeTest$Bar;";
    private static final String TAB = "Lapp/hushmessenger/extension/TabSwipeTest$Tab;";
    private static final String ROUTE = HOME + "->current()" + TAB + "|" + HOME + "->bar:" + BAR + "|"
        + BAR + "->tabs:Ljava/util/List;|" + HOME + "->select(" + TAB + ")V";

    private Context context;
    private FrameLayout page;

    @Test public void storyCardsThatScrollKeepEvenAQuickSwipe() {
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        home.open = TabSwipeTest.Tab.PEOPLE;
        HorizontalScrollView cards = new HorizontalScrollView(context);
        View strip = new View(context);
        strip.setMinimumWidth(3000);
        cards.addView(strip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TabSwipe.SwipeFrame frame = frame(home, cards);

        // The first move is already three slops along, so the cards never saw a move before the frame decided.
        drag(frame, 900, 500, -300);
        assertNull("The cards keep the swipe", home.picked);
        assertTrue("and scrolled with it", cards.getScrollX() > 0);
        assertSame(TabSwipeTest.Tab.PEOPLE, home.open);
    }

    @Test public void cardsAtTheirStartKeepASwipeTheyCantScrollAnyFurther() {
        // The report: left to right on People's cards opened Chats. At their start the cards can only go forward, so
        // a RecyclerView there never asks for the drag.
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        home.open = TabSwipeTest.Tab.PEOPLE;
        TabSwipe.SwipeFrame frame = frame(home, new Edge(context));

        drag(frame, 300, 500, 300);
        assertNull("Left to right stays on People", home.picked);
        drag(frame, 900, 500, -300);
        assertNull("and so does right to left", home.picked);
        assertSame(TabSwipeTest.Tab.PEOPLE, home.open);
    }

    @Test public void aViewThatAnswersForTheFingerItSawIsAskedAfterItSawIt() {
        // Compose answers canScrollHorizontally for the spot the last finger went down on, so the frame asks only once
        // the views under the finger have seen it go down.
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        Compose compose = new Compose(context);
        TabSwipe.SwipeFrame frame = frame(home, compose);

        drag(frame, 900, 500, -300);
        assertSame("Its right half doesn't scroll, so the tab changes", TabSwipeTest.Tab.PEOPLE, home.picked);
        home.picked = null;
        drag(frame, 400, 500, 300);
        assertNull("Its left half scrolls, so the swipe stays with it", home.picked);
    }

    @Test public void aRowInAMovedOrScrolledParentIsFoundWhereItShows() {
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        // Laid out 700 to 1000 inside a parent moved up by 300, it shows from 400 to 700.
        FrameLayout moved = new FrameLayout(context);
        moved.addView(new Edge(context), row(700));
        TabSwipe.SwipeFrame frame = frame(home, moved, fill());
        moved.setTranslationY(-300);

        drag(frame, 900, 800, -300);
        assertSame("Where it would be unmoved there's no row", TabSwipeTest.Tab.PEOPLE, home.picked);
        home.picked = null;
        drag(frame, 900, 500, -300);
        assertNull("Where it shows it keeps the swipe", home.picked);

        // The same for a parent scrolled down by 300, the way a list scrolls its rows.
        TabSwipeTest.Home other = new TabSwipeTest.Home();
        FrameLayout scrolled = new FrameLayout(context);
        scrolled.addView(new Edge(context), row(700));
        TabSwipe.SwipeFrame list = frame(other, scrolled, fill());
        scrolled.scrollTo(0, 300);

        drag(list, 900, 800, -300);
        assertSame(TabSwipeTest.Tab.PEOPLE, other.picked);
        other.picked = null;
        drag(list, 900, 500, -300);
        assertNull(other.picked);
    }

    @Test public void aHiddenRowOrTextThatScrollsDoesntHoldTheSwipe() {
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        Edge hidden = new Edge(context);
        TabSwipe.SwipeFrame frame = frame(home, hidden);
        hidden.setVisibility(View.INVISIBLE);
        drag(frame, 900, 500, -300);
        assertSame(TabSwipeTest.Tab.PEOPLE, home.picked);
        hidden.setVisibility(View.GONE);
        home.open = TabSwipeTest.Tab.INBOX;
        home.picked = null;
        drag(frame, 900, 500, -300);
        assertSame(TabSwipeTest.Tab.PEOPLE, home.picked);

        // A line of text too long for its view says it scrolls, but a chat row's name isn't a list.
        TabSwipeTest.Home other = new TabSwipeTest.Home();
        View text = new View(context) {
            @Override public boolean canScrollHorizontally(int direction) { return true; }

            @Override public boolean onTouchEvent(MotionEvent event) { return true; }
        };
        drag(frame(other, text), 900, 500, -300);
        assertSame(TabSwipeTest.Tab.PEOPLE, other.picked);
    }

    @Test public void aSwipeOnThePlainRowsUnderTheCardsStillChangesTab() {
        TabSwipeTest.Home home = new TabSwipeTest.Home();
        TabSwipe.SwipeFrame frame = frame(home, new Edge(context));

        drag(frame, 900, 1200, -300);
        assertSame(TabSwipeTest.Tab.PEOPLE, home.picked);
        drag(frame, 300, 1200, 300);
        assertSame("And back", TabSwipeTest.Tab.INBOX, home.picked);
        // A drag kept by the cards doesn't stay kept for the next touch.
        drag(frame, 900, 500, -300);
        drag(frame, 900, 1200, -300);
        assertSame(TabSwipeTest.Tab.PEOPLE, home.picked);
    }

    /** Stands in for a RecyclerView at its start: it can scroll forward, takes the touch and never asks to keep it. */
    static final class Edge extends FrameLayout {
        Edge(Context context) {
            super(context);
        }

        @Override public boolean canScrollHorizontally(int direction) {
            return direction > 0;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            return true;
        }
    }

    /** Stands in for a Compose view whose left half is a row that scrolls, answering for where the finger went down. */
    static final class Compose extends FrameLayout {
        private float lastDown = Float.NaN;

        Compose(Context context) {
            super(context);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) lastDown = event.getX();
            return super.dispatchTouchEvent(event);
        }

        @Override public boolean canScrollHorizontally(int direction) {
            return lastDown < getWidth() / 2f;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            return true;
        }
    }

    /** The tab's page: a list that takes touches, with [row] from 400 to 700 over it. */
    private TabSwipe.SwipeFrame frame(TabSwipeTest.Home home, View row) {
        return frame(home, row, row(400));
    }

    private TabSwipe.SwipeFrame frame(TabSwipeTest.Home home, View row, FrameLayout.LayoutParams where) {
        page = new FrameLayout(context);
        page.setLayoutParams(new ViewGroup.LayoutParams(1080, 1920));
        View list = new View(context) {
            @Override public boolean onTouchEvent(MotionEvent event) { return true; }
        };
        page.addView(list, fill());
        page.addView(row, where);
        View wrapped = TabSwipe.wrap(page, home, ROUTE);
        assertTrue(wrapped instanceof TabSwipe.SwipeFrame);
        wrapped.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        wrapped.layout(0, 0, 1080, 1920);
        return (TabSwipe.SwipeFrame) wrapped;
    }

    private static FrameLayout.LayoutParams row(int top) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 300);
        params.topMargin = top;
        return params;
    }

    private static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** A drag from [x],[y] that goes [sideways] in four moves of a quarter each, then lifts. */
    private static void drag(TabSwipe.SwipeFrame frame, float x, float y, int sideways) {
        frame.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0));
        for (int step = 1; step <= 4; step++) {
            frame.dispatchTouchEvent(MotionEvent.obtain(0, step * 10L, MotionEvent.ACTION_MOVE, x + sideways * step / 4f, y, 0));
        }
        frame.dispatchTouchEvent(MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, x + sideways, y, 0));
    }

    @Before public void reset() {
        context = RuntimeEnvironment.getApplication();
        Settings.initialize(context);
        Settings.preferences.edit().clear().putBoolean(TabSwipe.KEY, true).commit();
        Settings.activeAt.clear();
        Settings.hookErrors.clear();
        CrashGuard.resetForTests();
        restart();
    }

    /** What a Messenger restart clears: the answer the home screen's frame held for the run. */
    @After public void restart() {
        Settings.tabSwiping = null;
        Settings.bottomTabs.clear();
    }
}

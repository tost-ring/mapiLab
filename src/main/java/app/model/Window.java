package app.model;

import app.model.input.Keyboard;
import app.model.input.Mouse;
import app.model.variable.Fun;
import app.model.variable.NumberVar;
import app.model.variable.Playground;
import app.model.variable.Var;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLUtil;
import suite.suite.Slot;
import suite.suite.Subject;
import suite.suite.Suite;

import java.lang.reflect.InvocationTargetException;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window extends Playground {

    static Subject windows = Suite.set();

    public static void play(Subject sub) {
        glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err));
        if ( !glfwInit() ) throw new IllegalStateException("Unable to initialize GLFW");

        Window window = Window.create(
                sub.get(Window.class).orGiven(Window.class),
                sub.get("w").orGiven(800),
                sub.get("h").orGiven(600));

        glfwShowWindow(window.getGlid());

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glfwSwapInterval(1);

        while(windows.settled())
        {
//            float currentFrame = (float)glfwGetTime();
//            deltaTime = currentFrame - lastFrame;
//            lastFrame = currentFrame;

            glfwPollEvents();
            for(var s : windows.front()) {
                Window win = s.asExpected();
                win.play();
                if(glfwWindowShouldClose(win.getGlid()))windows.unset(win.getGlid());
            }
        }

        glfwTerminate();
    }

    public static Window create(Class<? extends Window> windowType, int width, int height) {
        Window window = null;
        try {
            window = windowType.getConstructor(int.class, int.class).newInstance(width, height);
            glfwMakeContextCurrent(window.getGlid());
            GL.createCapabilities();
            GLUtil.setupDebugMessageCallback();
            window.ready();
            long glid = window.getGlid();
            windows.setAt(Slot.PRIME, glid, window);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }

        return window;
    }

    final long glid;
    protected final Keyboard keyboard = new Keyboard();
    protected final Mouse mouse = new Mouse();
    protected final Var<Integer> width;
    protected final Var<Integer> height;

    public Window(int width, int height) {
        this.width = Var.create(width);
        this.height = Var.create(height);
        glid = glfwCreateWindow(this.width.get(), this.height.get(), "LearnOpenGL", NULL, NULL);
        if (glid == NULL) throw new RuntimeException("Window create failed");

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        glfwSetFramebufferSizeCallback(glid, (win, w, h) -> {
            glfwMakeContextCurrent(win);
            glViewport(0, 0, w, h);
            this.width.set(w);
            this.height.set(h);
        });

        glfwSetCursorPosCallback(glid, mouse::reportPositionEvent);
        glfwSetMouseButtonCallback(glid, mouse::reportMouseButtonEvent);
        glfwSetScrollCallback(glid, mouse::reportScrollEvent);

        glfwSetKeyCallback(glid, keyboard::reportKeyEvent);
        glfwSetCharModsCallback(glid, keyboard::reportCharEvent);
    }

    protected void ready() {}

    public long getGlid() {
        return glid;
    }

    public Var<Integer> getWidth() {
        return width;
    }

    public Var<Integer> getHeight() {
        return height;
    }

    public Keyboard getKeyboard() {
        return keyboard;
    }

    public Mouse getMouse() {
        return mouse;
    }

    public void setCursor(int cursor) {
        glfwSetInputMode(glid, GLFW_CURSOR, cursor);
    }

    public void setLockKeyModifiers(boolean lock) {
        glfwSetInputMode(glid, GLFW_LOCK_KEY_MODS, lock ? GLFW_TRUE : GLFW_FALSE);
    }

    public NumberVar pxFromLeft(Subject sub) {
        return NumberVar.compose(Suite.set("l", width).set("x", sub.direct()), "x * 2 / l - 1");
    }

    public NumberVar pxFromTop(Subject sub) {
        return NumberVar.compose(Suite.set("l", height).set("x", sub.direct()), "x * -2 / l + 1");
    }

    public NumberVar pxFromRight(Subject sub) {
        return NumberVar.compose(Suite.set("l", width).set("x", sub.direct()), "x * -2 / l + 1");
    }

    public NumberVar pxFromBottom(Subject sub) {
        return NumberVar.compose(Suite.set("l", height).set("x", sub.direct()), "x * 2 / l - 1");
    }

    public NumberVar pxWidth(Subject sub) {
        return NumberVar.compose(Suite.set("l", width).set("x", sub.direct()), "x  / l * 2");
    }

    public NumberVar pxHeight(Subject sub) {
        return NumberVar.compose(Suite.set("l", height).set("x", sub.direct()), "x  / l * 2");
    }
}

import java.lang.reflect.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.Font;

public final class EditorRuntimeTest {
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Class<?> options = Class.forName("oracle.ide.ceditor.options.FontSizeOptions");
        Object instance = options.getConstructor().newInstance();
        Method set = options.getMethod("setFontSize", int.class), get = options.getMethod("getFontSize");
        for (int[] pair : new int[][] {{1,7},{24,24},{34,34},{50,50},{200,50}}) {
            set.invoke(instance, pair[0]);
            require(((Integer) get.invoke(instance)) == pair[1], "Size clamp failed: " + pair[0]);
        }
        System.out.println("PASS patched Oracle FontSizeOptions: 7/24/34/50/200pt boundaries");
        SwingUtilities.invokeAndWait(() -> {
            try {
                Class<?> panelClass = Class.forName("oracle.ide.ceditor.options.FontSizeOptionsPanel");
                Object panel = panelClass.getConstructor().newInstance();
                Field field = panelClass.getDeclaredField("comboBox_fontSize"); field.setAccessible(true);
                JComboBox<?> sizes = (JComboBox<?>) field.get(panel);
                require(sizes.getItemCount() == 44, "Menu should contain every size 7..50");
                for (int i=0; i<44; i++) require(sizes.getItemAt(i).equals(i+7), "Menu item incorrect: " + i);
                sizes.setSelectedItem(34); require(sizes.getSelectedItem().equals(34), "Existing 34pt value not selectable");
                sizes.setSelectedItem(50); require(sizes.getSelectedItem().equals(50), "50pt not selectable");
                System.out.println("PASS patched Oracle editor panel: all 44 sizes selectable, including 34 and 50pt");
                Class<?> editorClass = Class.forName("oracle.javatools.editor.BasicEditorPane");
                JComponent editor = (JComponent) editorClass.getConstructor().newInstance();
                editorClass.getMethod("setText", String.class).invoke(editor, "select 1 from dual;");
                editor.setSize(1000, 400);
                Class<?> propertiesClass = Class.forName("oracle.javatools.editor.EditorProperties");
                Object properties = propertiesClass.getMethod("getProperties").invoke(null);
                Method baseFont = propertiesClass.getMethod("setBaseFont", String.class, int.class);
                Method readFont = propertiesClass.getMethod("getBaseFont");
                Class<?> pluginClass = Class.forName("oracle.ide.ceditor.ZoomTextPlugin");
                Object plugin = pluginClass.getConstructor().newInstance();
                pluginClass.getMethod("install", editorClass).invoke(plugin, editor);
                Field listenerField = pluginClass.getDeclaredField("listener"); listenerField.setAccessible(true);
                MouseWheelListener listener = (MouseWheelListener) listenerField.get(plugin);
                for (int[] pair : new int[][] {{24,-1,25},{49,-1,50},{50,-5,50},{7,5,7}}) {
                    baseFont.invoke(properties, "Monospaced", pair[0]);
                    MouseWheelEvent event = new MouseWheelEvent(editor, MouseEvent.MOUSE_WHEEL, 0, InputEvent.CTRL_DOWN_MASK,
                        100, 100, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, pair[1]);
                    listener.mouseWheelMoved(event);
                    require(((Font) readFont.invoke(properties)).getSize() == pair[2], "Wheel clamp failed at " + pair[0]);
                }
                pluginClass.getMethod("deinstall", editorClass).invoke(plugin, editor);
                System.out.println("PASS patched Oracle Ctrl+wheel: 24 to 25, 49 to 50, max 50, min 7pt");
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
}

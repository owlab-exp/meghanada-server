package meghanada.utils;

import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ClassName {

    private static final Logger log = LogManager.getLogger(ClassName.class);

    private final String rawName;
    private final int typeIndex;
    private final int typeLastIndex;
    private final int arrayIndex;

    public ClassName(String name) {
        this.rawName = ClassNameUtils.vaArgsToArray(name);
        this.typeIndex = this.rawName.indexOf('<');
        this.typeLastIndex = this.rawName.lastIndexOf('>');
        this.arrayIndex = this.rawName.indexOf('[');
    }

    public boolean hasTypeParameter() {
        return this.typeIndex > 0;
    }

    public String getName() {
        String name = ClassNameUtils.removeCaptureAndWildCard(this.rawName);
        if (typeIndex >= 0) {
            String fst = name.substring(0, typeIndex);
            String sec = name.substring(typeLastIndex + 1, name.length());
            name = fst + sec;
        }

        int arrayIndex = name.indexOf('[');
        if (arrayIndex >= 0) {
            name = name.substring(0, arrayIndex);
        }
        return name;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("rawName", rawName)
                .toString();
    }
}

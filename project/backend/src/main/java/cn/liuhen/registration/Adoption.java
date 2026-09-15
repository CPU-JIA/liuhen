package cn.liuhen.registration;

/** 采用方式，四选一（实验 2 对比记录 A-7）。 */
public enum Adoption {
    DIRECT("直接采用"), MODIFIED("修改后采用"), REFERENCE("仅作参考"), NOT_USED("未采用");

    private final String label;

    Adoption(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

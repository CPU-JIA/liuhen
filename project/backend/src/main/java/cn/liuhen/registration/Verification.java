package cn.liuhen.registration;

public enum Verification {
    SOURCE_CHECK("核对原文"), RUN_TEST("运行验证"), TEXTBOOK("与教材比对"), NONE("未核对");

    private final String label;

    Verification(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

package cn.liuhen.registration;

/** 使用环节。与规则场景名的对应关系是规则引擎判断"超出允许范围"的依据。 */
public enum Stage {
    RESEARCH("查资料"), OUTLINE("生成大纲"), BODY("生成正文"), CODE("生成代码框架"), DATA("生成数据"), POLISH("改语法"), OTHER("其他");

    private final String sceneName;

    Stage(String sceneName) {
        this.sceneName = sceneName;
    }

    public String sceneName() {
        return sceneName;
    }
}

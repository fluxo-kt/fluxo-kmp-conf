
# ProGuard/R8 keep rules
# Auto-generated by Fluxo task :generateShrinkerKeepRulesFromApi
# From API reports (with sha256short):
# - api/check-gradle-plugin.api (53f1538)
# DO NOT EDIT MANUALLY!

-keep,includedescriptorclasses public final class NewPlugin {
    public <init>();
    public synthetic void apply(java.lang.Object);
    public void apply(org.gradle.api.Project);
}


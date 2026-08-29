module.exports = {
  platform: "github",
  repositories: ["funfix/tasks"],
  branchPrefix: "renovate/",
  onboarding: false,
  requireConfig: "optional",
  recreateWhen: "always",
  prHourlyLimit: 0,
  separateMajorMinor: false,

  extends: [":dependencyDashboard"],

  enabledManagers: ["github-actions", "gradle", "gradle-wrapper"],

  ignorePaths: ["**/.gradle/**"],

  packageRules: [
    {
      description: "Group all dependency updates into a single PR",
      matchManagers: ["github-actions", "gradle", "gradle-wrapper"],
      groupName: "dependencies",
      groupSlug: "all-dependencies",
      group: {
        commitMessageTopic: "dependencies",
        commitMessageExtra: "",
      },
    },
    {
      description: "Only use stable dotted numeric JVM dependency versions",
      matchManagers: ["gradle", "gradle-wrapper"],
      allowedVersions: "/^\\d+(?:\\.\\d+)+$/",
    },
    {
      description: "Pin Error Prone to 2.42.x (last version supporting JDK 17)",
      matchManagers: ["gradle"],
      matchPackageNames: ["com.google.errorprone:error_prone_core"],
      allowedVersions: "/^2\\.42\\.\\d+$/",
    },
    {
      description: "Wait one week before proposing dependency updates",
      matchManagers: ["github-actions", "gradle", "gradle-wrapper"],
      minimumReleaseAge: "7 days",
      minimumReleaseAgeBehaviour: "timestamp-optional",
    },
  ],
};

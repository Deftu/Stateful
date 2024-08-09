# Stateful

[![wakatime](https://wakatime.com/badge/user/25be8ed5-7461-4fcf-93f7-0d88a7692cca/project/018c9cc0-7f56-4cb1-b257-8fd5f81573b6.svg)](https://wakatime.com/badge/user/25be8ed5-7461-4fcf-93f7-0d88a7692cca/project/018c9cc0-7f56-4cb1-b257-8fd5f81573b6)

---

[![Discord Badge](https://raw.githubusercontent.com/intergrav/devins-badges/v2/assets/cozy/social/discord-singular_64h.png)](https://s.deftu.dev/discord)
[![Ko-Fi Badge](https://raw.githubusercontent.com/intergrav/devins-badges/v2/assets/cozy/donate/kofi-singular_64h.png)](https://s.deftu.dev/kofi)

---

## Setup

### Repository

Stateful is currently only available on my **snapshots** repository. Please keep in mind that this may change in the future.

<details>
    <summary>Groovy (.gradle)</summary>

```gradle
maven {
    name = "Deftu Releases"
    url = "https://maven.deftu.dev/snapshots"
}
```
</details>

<details>
    <summary>Kotlin (.gradle.kts)</summary>

```kotlin
maven(url = "https://maven.deftu.dev/snapshots") {
    name = "Deftu Releases"
}
```
</details>

### Dependency

![Repository badge](https://maven.deftu.dev/api/badge/latest/snapshots/dev/deftu/stateful?color=C33F3F&name=Stateful)

<details>
    <summary>Groovy (.gradle)</summary>

```gradle
implementation "dev.deftu:stateful:<VERSION>"
```

</details>

<details>
    <summary>Kotlin (.gradle.kts)</summary>

```gradle
implementation("dev.deftu:stateful:<VERSION>")
```

</details>

---

[![BisectHosting](https://www.bisecthosting.com/partners/custom-banners/8fb6621b-811a-473b-9087-c8c42b50e74c.png)](https://s.deftu.dev/bisect)

---

**This project is licensed under [LGPL-3.0][lgpl]**\
**&copy; 2024 Deftu**

[lgpl]: https://www.gnu.org/licenses/lgpl-3.0.en.html

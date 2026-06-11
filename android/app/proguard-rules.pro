# Firebase + Firestore model classes are accessed reflectively; keep them.
-keepclassmembers class com.bountyradar.app.data.** { *; }
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

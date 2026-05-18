# Future Ideas

## Android Health Connect API

The [Health Connect API](https://developer.android.com/health-and-fitness/guides/health-connect) allows sharing health data between Android apps.

### What we could expose
- **Heart rate**: Write resting and average BPM as `HeartRateRecord`
- **Steps**: Write daily step totals as `StepsRecord`
- **Sleep**: Write sleep sessions as `SleepSessionRecord` with stages
- **Calories**: Write active calories as `ActiveCaloriesBurnedRecord`

### What we could import
- **Weight**: Read `WeightRecord` from other apps (e.g., the user's workout app) to improve calorie calculations without requiring manual entry
- **Exercise sessions**: Import exercise data from other apps to correlate with HR zones

### Implementation notes
- Requires `androidx.health.connect:connect-client` dependency
- User must grant permissions per data type via the Health Connect app
- Data should be written after each sync, tagged with our app's data origin
- Consider a toggle in Settings to enable/disable Health Connect integration

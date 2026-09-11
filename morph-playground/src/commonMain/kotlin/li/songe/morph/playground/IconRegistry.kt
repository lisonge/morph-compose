package li.songe.morph.playground

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AirplaneTicket
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.AddChart
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleLeft
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

internal data class IconEntry(
    val name: String,
    val imageVector: ImageVector,
)

internal val iconEntries =
    listOf(
        IconEntry("Menu", Icons.Filled.Menu),
        IconEntry("Close", Icons.Filled.Close),
        IconEntry("Play", Icons.Filled.PlayArrow),
        IconEntry("Pause", Icons.Filled.Pause),
        IconEntry("Favorite border", Icons.Filled.FavoriteBorder),
        IconEntry("Favorite", Icons.Filled.Favorite),
        IconEntry("Add", Icons.Filled.Add),
        IconEntry("Remove", Icons.Filled.Remove),
        IconEntry("Home", Icons.Filled.Home),
        IconEntry("Search", Icons.Filled.Search),
        IconEntry("Settings", Icons.Filled.Settings),
        IconEntry("Person", Icons.Filled.Person),
        IconEntry("Star", Icons.Filled.Star),
        IconEntry("Star border", Icons.Filled.StarBorder),
        IconEntry("Check", Icons.Filled.Check),
        IconEntry("Arrow back", Icons.AutoMirrored.Filled.ArrowBack),
        IconEntry("Arrow forward", Icons.AutoMirrored.Filled.ArrowForward),
        IconEntry("Arrow up", Icons.Filled.ArrowUpward),
        IconEntry("Arrow down", Icons.Filled.ArrowDownward),
        IconEntry("Refresh", Icons.Filled.Refresh),
        IconEntry("Delete", Icons.Filled.Delete),
        IconEntry("Edit", Icons.Filled.Edit),
        IconEntry("Share", Icons.Filled.Share),
        IconEntry("Send", Icons.AutoMirrored.Filled.Send),
        IconEntry("Download", Icons.Filled.Download),
        IconEntry("Upload", Icons.Filled.Upload),
        IconEntry("Lock", Icons.Filled.Lock),
        IconEntry("Lock open", Icons.Filled.LockOpen),
        IconEntry("Visibility", Icons.Filled.Visibility),
        IconEntry("Visibility off", Icons.Filled.VisibilityOff),
        IconEntry("Notifications", Icons.Filled.Notifications),
        IconEntry("Notifications none", Icons.Filled.NotificationsNone),
        IconEntry("Email", Icons.Filled.Email),
        IconEntry("Phone", Icons.Filled.Phone),
        IconEntry("Location", Icons.Filled.LocationOn),
        IconEntry("Calendar", Icons.Filled.CalendarMonth),
        IconEntry("Time", Icons.Filled.AccessTime),
        IconEntry("Camera", Icons.Filled.CameraAlt),
        IconEntry("Photo", Icons.Filled.Photo),
        IconEntry("Mic", Icons.Filled.Mic),
        IconEntry("Volume", Icons.AutoMirrored.Filled.VolumeUp),
        IconEntry("Muted", Icons.AutoMirrored.Filled.VolumeOff),
        IconEntry("Wi-Fi", Icons.Filled.Wifi),
        IconEntry("Bluetooth", Icons.Filled.Bluetooth),
        IconEntry("Cloud", Icons.Filled.Cloud),
        IconEntry("Folder", Icons.Filled.Folder),
        IconEntry("Info", Icons.Filled.Info),
        IconEntry("Warning", Icons.Filled.Warning),
        IconEntry("Help", Icons.AutoMirrored.Filled.Help),
        IconEntry("Cart", Icons.Filled.ShoppingCart),
        IconEntry("ABC", Icons.Filled.Abc),
        IconEntry("AC unit", Icons.Filled.AcUnit),
        IconEntry("Access alarm", Icons.Filled.AccessAlarm),
        IconEntry("Accessibility", Icons.Filled.Accessibility),
        IconEntry("Account balance", Icons.Filled.AccountBalance),
        IconEntry("Account tree", Icons.Filled.AccountTree),
        IconEntry("ADB", Icons.Filled.Adb),
        IconEntry("Add alert", Icons.Filled.AddAlert),
        IconEntry("Add box", Icons.Filled.AddBox),
        IconEntry("Add business", Icons.Filled.AddBusiness),
        IconEntry("Add card", Icons.Filled.AddCard),
        IconEntry("Add chart", Icons.Filled.AddChart),
        IconEntry("Add comment", Icons.Filled.AddComment),
        IconEntry("Add home", Icons.Filled.AddHome),
        IconEntry("Add link", Icons.Filled.AddLink),
        IconEntry("Add location", Icons.Filled.AddLocation),
        IconEntry("Add photo", Icons.Filled.AddPhotoAlternate),
        IconEntry("Add reaction", Icons.Filled.AddReaction),
        IconEntry("Add cart", Icons.Filled.AddShoppingCart),
        IconEntry("Add task", Icons.Filled.AddTask),
        IconEntry("Adjust", Icons.Filled.Adjust),
        IconEntry("Agriculture", Icons.Filled.Agriculture),
        IconEntry("Air", Icons.Filled.Air),
        IconEntry("Airplane ticket", Icons.AutoMirrored.Filled.AirplaneTicket),
        IconEntry("Airplay", Icons.Filled.Airplay),
        IconEntry("Airport shuttle", Icons.Filled.AirportShuttle),
        IconEntry("Alarm", Icons.Filled.Alarm),
        IconEntry("Album", Icons.Filled.Album),
        IconEntry("Alternate email", Icons.Filled.AlternateEmail),
        IconEntry("Analytics", Icons.Filled.Analytics),
        IconEntry("Anchor", Icons.Filled.Anchor),
        IconEntry("Android", Icons.Filled.Android),
        IconEntry("Animation", Icons.Filled.Animation),
        IconEntry("Apartment", Icons.Filled.Apartment),
        IconEntry("API", Icons.Filled.Api),
        IconEntry("Apps", Icons.Filled.Apps),
        IconEntry("Architecture", Icons.Filled.Architecture),
        IconEntry("Archive", Icons.Filled.Archive),
        IconEntry("Arrow circle down", Icons.Filled.ArrowCircleDown),
        IconEntry("Arrow circle left", Icons.Filled.ArrowCircleLeft),
        IconEntry("Arrow circle right", Icons.Filled.ArrowCircleRight),
        IconEntry("Arrow circle up", Icons.Filled.ArrowCircleUp),
        IconEntry("Article", Icons.AutoMirrored.Filled.Article),
        IconEntry("Assessment", Icons.Filled.Assessment),
        IconEntry("Attach file", Icons.Filled.AttachFile),
        IconEntry("Attach money", Icons.Filled.AttachMoney),
        IconEntry("Audiotrack", Icons.Filled.Audiotrack),
        IconEntry("Auto awesome", Icons.Filled.AutoAwesome),
        IconEntry("Auto fix", Icons.Filled.AutoFixHigh),
        IconEntry("Backup", Icons.Filled.Backup),
    ).also { entries ->
        check(entries.size == 100) { "The icon registry must expose exactly 100 icons" }
    }

internal val iconCount: Int
    get() = iconEntries.size

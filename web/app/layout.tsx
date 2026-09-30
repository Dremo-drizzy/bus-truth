import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Bus Truth — live map",
  description: "Where Halifax Transit's buses actually are, from the agency's own feed.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
